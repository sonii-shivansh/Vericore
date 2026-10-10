package com.vericore.core.workflow

import com.vericore.cli.CodeParallelParser
import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.ai.GroundedEvidenceBuilder
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.evidence.SemanticEvidenceGraphBuilder
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.intelligence.ChangeSet
import com.vericore.core.intelligence.DecisionProvenance
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.EngineeringPlanRequest
import com.vericore.core.planner.EngineeringPlanner
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EngineeringPreparationResult(
    val schemaVersion: String = "1.2",
    val repository: String,
    val changeSet: ChangeSet,
    val evidence: GroundedEvidence,
    val plan: EngineeringPlan,
    val contract: AgentChangeContract,
    val provenance: DecisionProvenance = DecisionProvenance.create("prepare", null, "1.0", emptyList()),
    val evidenceGraphDigest: String = "",
    val evidenceGraphNodeCount: Int = 0,
    val evidenceGraphEdgeCount: Int = 0
)

/** Builds a reusable evidence snapshot, deterministic plan, and repository-bound change contract before coding. */
object EngineeringPreparation {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun prepare(repoPath: String, changeSummary: String, plannedPaths: List<String> = emptyList()): EngineeringPreparationResult {
        val root = File(repoPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repoPath" }
        val config = ConfigLoader.loadForRepository(root.path)

        val scanner = RepositoryScanner(config)
        val cached = loadReusablePreparationInputs(root, scanner)
        val snapshot: AnalysisSnapshot
        val evidence: GroundedEvidence
        if (cached != null) {
            snapshot = cached.first
            evidence = cached.second
        } else {
            val files = scanner.scan(root.path)
            require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
            val parser = CodeParallelParser(CacheManager())
            val parsed = parser.parseFiles(files)
            val parseFailures = parser.lastWarningCount
            val enriched = OptimizedGitAnalyzer().analyze(root.path, parsed)
            val graph = RobustDependencyGraph()
            graph.build(enriched).getOrThrow()
            graph.analyze().getOrThrow()
            snapshot = AnalysisSnapshotBuilder.build(
                repositoryPath = root.path,
                parsedFiles = enriched,
                graph = graph.graph,
                pageRankScores = graph.pageRankScores,
                hasCycles = graph.hasCycles,
                parseFailures = parseFailures
            )
            evidence = GroundedEvidenceBuilder.fromSnapshot(snapshot)
        }

        val evidenceGraph = SemanticEvidenceGraphBuilder.build(snapshot, evidence)
        val changeSet = runCatching { com.vericore.core.intelligence.GitChangeSetBuilder.fromWorkingTree(root.path) }
            .getOrElse { ChangeSet(emptyList(), source = "not-a-git-change-set") }
        val initialPlan = EngineeringPlanner().plan(
            EngineeringPlanRequest(
                changeSummary = changeSummary,
                changedPaths = plannedPaths.ifEmpty { changeSet.files.map { it.path } },
                evidence = evidence,
                repositoryPath = root.path
            )
        )
        val preparedHead = RepositoryState.head(root.path).orEmpty()
        val contract = AgentChangeContract.fromPlan(initialPlan, root.path, preparedHead)
        val plan = initialPlan.copy(contractFingerprint = contract.fingerprint)
        val provenance = DecisionProvenance.capture(
            repoPath = root.path,
            operation = "prepare",
            analysisSchemaVersion = snapshot.schemaVersion,
            evidenceIds = evidence.citations.map { it.id }
        )
        return EngineeringPreparationResult(
            repository = root.path,
            changeSet = changeSet,
            evidence = evidence,
            plan = plan,
            contract = contract,
            provenance = provenance,
            evidenceGraphDigest = evidenceGraph.digest(),
            evidenceGraphNodeCount = evidenceGraph.nodes.size,
            evidenceGraphEdgeCount = evidenceGraph.edges.size
        )
    }

    private fun loadReusablePreparationInputs(root: File, scanner: RepositoryScanner): Pair<AnalysisSnapshot, GroundedEvidence>? {
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        val evidenceFile = root.resolve("output/grounded-evidence.json")
        if (!snapshotFile.isFile || !evidenceFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
            val evidence = json.decodeFromString<GroundedEvidence>(evidenceFile.readText())
            val currentHead = RepositoryState.head(root.path)
            val currentState = EngineeringContextEngine.snapshot(root, scanner)
            require(currentState.changedPaths.all { changedPath ->
                changedPath.endsWith(".kt") || changedPath.endsWith(".java")
            }) {
                "Cached preparation cannot be reused when non-source repository files have changed"
            }
            require(snapshot.repository.path == root.path) {
                "Cached analysis snapshot belongs to a different repository"
            }
            require(snapshot.repository.repositoryCommit == currentHead && currentState.repositoryCommit == currentHead) {
                "Cached analysis snapshot is stale"
            }
            require(
                !snapshot.repository.repositoryStateDigest.isNullOrBlank() &&
                    snapshot.repository.repositoryStateDigest == currentState.snapshotDigest
            ) {
                "Cached analysis snapshot source state is stale"
            }
            require(evidence.isBoundTo(snapshot, root.path)) {
                "Cached grounded evidence is not bound to the current analysis snapshot"
            }
            snapshot to evidence
        }.getOrNull()
    }
}
