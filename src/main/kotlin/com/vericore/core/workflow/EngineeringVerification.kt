package com.vericore.core.workflow

import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.intelligence.ChangeImpactEngine
import com.vericore.core.intelligence.DecisionProvenance
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.vericore.cli.CodeParallelParser
import com.vericore.core.cache.CacheManager
import com.vericore.core.planner.EngineeringPlan
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class VerificationExecutionResult(
    val commands: List<VerificationCommandResult> = emptyList(),
    val commandsDeclared: Int = commands.size,
    val commandsExecuted: Int = commands.count { it.executed },
    val allCommandsExecuted: Boolean = commands.isNotEmpty() && commands.all { it.executed },
    val allCommandsPassed: Boolean = commands.isNotEmpty() && commands.all { it.executed && !it.timedOut && it.exitCode == 0 },
    val executionComplete: Boolean = commands.isNotEmpty() && commands.all { it.executed }
)

@Serializable
data class EngineeringVerificationResult(
    val schemaVersion: String = "1.3",
    val repository: String,
    val safety: ChangeSafetyResult,
    val prIntelligence: com.vericore.core.intelligence.PRIntelligenceResult,
    val architecture: ArchitectureIntelligenceResult,
    val verificationCommands: List<String>,
    val verification: VerificationExecutionResult,
    val status: SafetyStatus,
    val reasons: List<String> = emptyList(),
    val provenance: DecisionProvenance = DecisionProvenance.create("verify", null, "1.0", emptyList()),
    val contract: AgentChangeContractResult? = null
)

/** Runs deterministic post-change checks against the exact persisted contract produced by prepare. */
object EngineeringVerification {
    suspend fun verify(repoPath: String, plan: EngineeringPlan, contract: AgentChangeContract): EngineeringVerificationResult {
        val root = File(repoPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repoPath" }
        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
        val parser = CodeParallelParser(CacheManager())
        val parsed = parser.parseFiles(files)
        val parseFailures = parser.lastWarningCount
        val enriched = OptimizedGitAnalyzer().analyze(root.path, parsed)
        val graph = RobustDependencyGraph()
        graph.build(enriched).getOrThrow()
        graph.analyze().getOrThrow()

        val changeSet = GitChangeSetBuilder.fromWorkingTree(root.path)
        val plannedPaths = plan.plannedPaths
        val safety = ChangeSafetyAnalyzer.verify(changeSet.files, plannedPaths)
        val packageByPath = enriched.associate { file -> root.toPath().relativize(file.file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/') to file.packageName }
        val absoluteByRelative = enriched.associate { file -> root.toPath().relativize(file.file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/') to file.file.absolutePath.replace('\\', '/') }
        val changedAbsolute = changeSet.files.mapNotNull { absoluteByRelative[it.path] }
        val churn = enriched.associate { it.file.absolutePath.replace('\\', '/') to it.gitMetadata.changeFrequency }
        val packages = enriched.associate { it.file.absolutePath.replace('\\', '/') to it.packageName }
        val impact = ChangeImpactEngine.analyze(graph.graph, changedAbsolute, graph.pageRankScores, churn, packages)
        val snapshot = AnalysisSnapshotBuilder.build(
            repositoryPath = root.path,
            parsedFiles = enriched,
            graph = graph.graph,
            pageRankScores = graph.pageRankScores,
            hasCycles = graph.hasCycles,
            parseFailures = parseFailures
        )
        val risks = com.vericore.core.intelligence.EngineeringRiskEngine.calculate(snapshot)
        val toRelativePath: (String) -> String = { path ->
            val candidate = File(path).toPath()
            val absolute = if (candidate.isAbsolute) candidate else root.toPath().resolve(candidate)
            root.toPath().relativize(absolute.normalize()).toString().replace('\\', '/')
        }
        val tests = impact.nodes.filter { it.relationship == com.vericore.core.intelligence.ImpactRelationship.TEST_CANDIDATE }.map { toRelativePath(it.path) }
        val pr = com.vericore.core.intelligence.PRIntelligenceEngine.analyze(changeSet, impact, risks, packageByPath, tests, toRelativePath)
        val architecture = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)

        val currentHead = RepositoryState.head(root.path).orEmpty()
        val expectedContractFingerprint = AgentChangeContract.fingerprintFor(contract)
        val expectedPlanFingerprint = AgentChangeContract.fromPlan(plan, root.path, contract.preparedHead).fingerprint
        val reasons = buildList {
            if (contract.fingerprint != expectedContractFingerprint) add("The persisted agent change contract fingerprint is invalid or tampered.")
            if (contract.repository != root.path) add("The contract belongs to a different repository: ${contract.repository}")
            if (plan.contractFingerprint != contract.fingerprint) add("The engineering plan is not bound to the persisted contract fingerprint.")
            if (expectedPlanFingerprint != contract.fingerprint) add("The supplied plan does not match the persisted contract contents.")
            if (contract.preparedHead.isNotBlank() && currentHead.isNotBlank() && contract.preparedHead != currentHead) add("The repository HEAD changed after prepare (${contract.preparedHead} -> $currentHead); the contract is stale.")
        }
        val contractValid = reasons.isEmpty()
        val contractResult = AgentChangeContractResult(contract, contractValid, reasons)
        val execution = if (contractValid && safety.status != SafetyStatus.FAIL) {
            VerificationExecutionResult(commands = VerificationCommandExecutor.execute(root, contract.verificationCommands))
        } else {
            VerificationExecutionResult()
        }
        val status = when {
            !contractValid -> SafetyStatus.FAIL
            safety.status == SafetyStatus.FAIL -> SafetyStatus.FAIL
            !execution.allCommandsPassed -> SafetyStatus.FAIL
            safety.status == SafetyStatus.REVIEW_REQUIRED || pr.aggregateSeverity.name == "CRITICAL" -> SafetyStatus.REVIEW_REQUIRED
            else -> SafetyStatus.PASS
        }
        val verificationReasons = buildList {
            addAll(reasons)
            addAll(safety.reasons)
            if (execution.commands.isEmpty()) {
                add("No verification commands were executed; verification cannot pass without a successful verification command.")
            } else {
                execution.commands.filter { !it.executed || it.timedOut || it.exitCode != 0 }.forEach { command ->
                    val outcome = when {
                        !command.executed -> "was not executed"
                        command.timedOut -> "timed out"
                        else -> "exited with code ${command.exitCode}"
                    }
                    add("Verification command '${command.command}' $outcome.")
                }
            }
        }.distinct()
        val provenance = DecisionProvenance.capture(root.path, "verify", snapshot.schemaVersion, contract.evidenceIds)
        return EngineeringVerificationResult(
            repository = root.path,
            safety = safety,
            prIntelligence = pr,
            architecture = architecture,
            verificationCommands = contract.verificationCommands,
            verification = execution,
            status = status,
            reasons = verificationReasons,
            provenance = provenance,
            contract = contractResult
        )
    }
}
