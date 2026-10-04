package com.vericore.core.workflow

import com.vericore.cli.CodeParallelParser
import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.ai.GroundedEvidenceBuilder
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.evidence.SemanticEvidenceGraphBuilder
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.ChangeSet
import com.vericore.core.intelligence.DecisionProvenance
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.EngineeringPlanRequest
import com.vericore.core.planner.EngineeringPlanner
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import kotlinx.serialization.Serializable

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
    suspend fun prepare(repoPath: String, changeSummary: String): EngineeringPreparationResult {
        val root = File(repoPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repoPath" }
        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
        val parsed = CodeParallelParser(CacheManager()).parseFiles(files)
        val parseFailures = files.size - parsed.size
        val enriched = OptimizedGitAnalyzer().analyze(root.path, parsed)
        val graph = RobustDependencyGraph()
        graph.build(enriched).getOrThrow()
        graph.analyze().getOrThrow()
        val snapshot = AnalysisSnapshotBuilder.build(
            repositoryPath = root.path,
            parsedFiles = enriched,
            graph = graph.graph,
            pageRankScores = graph.pageRankScores,
            hasCycles = graph.hasCycles,
            parseFailures = parseFailures
        )
        val evidence = GroundedEvidenceBuilder.fromSnapshot(snapshot)
        val evidenceGraph = SemanticEvidenceGraphBuilder.build(snapshot, evidence)
        val changeSet = runCatching { com.vericore.core.intelligence.GitChangeSetBuilder.fromWorkingTree(root.path) }
            .getOrElse { ChangeSet(emptyList(), source = "not-a-git-change-set") }
        val initialPlan = EngineeringPlanner().plan(
            EngineeringPlanRequest(
                changeSummary = changeSummary,
                changedPaths = changeSet.files.map { it.path },
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
}
