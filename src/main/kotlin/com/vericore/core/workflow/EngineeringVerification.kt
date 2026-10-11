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
import com.vericore.core.planner.VerificationEvidenceIds
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
    val schemaVersion: String = "1.5",
    val repository: String,
    val safety: ChangeSafetyResult,
    val prIntelligence: com.vericore.core.intelligence.PRIntelligenceResult,
    val architecture: ArchitectureIntelligenceResult,
    val verificationCommands: List<String>,
    val verification: VerificationExecutionResult,
    val status: SafetyStatus,
    val reasons: List<String> = emptyList(),
    val provenance: DecisionProvenance = DecisionProvenance.create("verify", null, "1.0", emptyList()),
    val contract: AgentChangeContractResult? = null,
    val analysisInputDigest: String,
    val candidateStateDigest: String,
    val postVerificationCandidateDigest: String,
    val candidateStateStable: Boolean,
    val candidateStateFileCount: Int,
    val postVerificationFileCount: Int,
    val specificationEvaluation: ChangeSpecificationEvaluation? = null
)

/** Runs deterministic post-change checks against the exact persisted contract produced by prepare. */
object EngineeringVerification {
    suspend fun verify(repoPath: String, plan: EngineeringPlan, contract: AgentChangeContract): EngineeringVerificationResult {
        val root = File(repoPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repoPath" }
        val headBeforeAnalysis = RepositoryState.head(root.path).orEmpty()
        val analysisInput = CandidateStateSnapshotter.capture(root)
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

        // The analysis and the verification commands must refer to the same candidate state.
        // A content digest catches uncommitted and untracked changes that do not move Git HEAD.
        val candidateAtVerification = CandidateStateSnapshotter.capture(root)
        val analysisStateStable = analysisInput.digest == candidateAtVerification.digest
        val currentHead = RepositoryState.head(root.path).orEmpty()
        val expectedContractFingerprint = AgentChangeContract.fingerprintFor(contract)
        val expectedPlanFingerprintResult = runCatching {
            AgentChangeContract.fromPlan(plan, root.path, contract.preparedHead).fingerprint
        }
        val expectedPlanFingerprint = expectedPlanFingerprintResult.getOrNull().orEmpty()
        val planSpecificationError = expectedPlanFingerprintResult.exceptionOrNull()?.message
        val contractReasons = buildList {
            if (contract.fingerprint != expectedContractFingerprint) add("The persisted agent change contract fingerprint is invalid or tampered.")
            if (planSpecificationError != null) add("The supplied plan contains an invalid structured specification: $planSpecificationError")
            if (contract.repository != root.path) add("The contract belongs to a different repository: ${contract.repository}")
            if (plan.contractFingerprint != contract.fingerprint) add("The engineering plan is not bound to the persisted contract fingerprint.")
            if (expectedPlanFingerprint != contract.fingerprint) add("The supplied plan does not match the persisted contract contents.")
            if (contract.preparedHead.isNotBlank() && currentHead.isNotBlank() && contract.preparedHead != currentHead) add("The repository HEAD changed after prepare (${contract.preparedHead} -> $currentHead); the contract is stale.")
        }
        val contractValid = contractReasons.isEmpty()
        val contractResult = AgentChangeContractResult(contract, contractValid, contractReasons)
        val execution = if (contractValid && safety.status != SafetyStatus.FAIL && analysisStateStable) {
            VerificationExecutionResult(commands = VerificationCommandExecutor.execute(root, contract.verificationCommands))
        } else {
            VerificationExecutionResult()
        }
        val availableSpecificationEvidenceIds = contract.evidenceIds.toSet() +
            contract.verificationCommands.indices.map { VerificationEvidenceIds.command(it + 1) }
        val specificationEvaluation = ChangeSpecificationEvaluator.evaluate(
            contract.specification,
            availableSpecificationEvidenceIds,
            execution.commands
        )
        val postVerificationCandidate = CandidateStateSnapshotter.capture(root)
        val headAfterVerification = RepositoryState.head(root.path).orEmpty()
        val candidateStateDriftReasons = buildList {
            if (headBeforeAnalysis != currentHead) {
                add("Git HEAD changed during analysis; the verification contract and analysis may refer to different repository revisions.")
            }
            if (!analysisStateStable) {
                add("Repository candidate state changed during analysis; verification commands were not executed because the analysis may be stale.")
            }
            if (candidateAtVerification.digest != postVerificationCandidate.digest) {
                add("Repository candidate state changed during verification; command results cannot be attributed to one stable candidate.")
            }
            if (currentHead != headAfterVerification) {
                add("Git HEAD changed during verification; command results cannot be attributed to one stable repository revision.")
            }
        }
        val status = when {
            !contractValid -> SafetyStatus.FAIL
            safety.status == SafetyStatus.FAIL -> SafetyStatus.FAIL
            candidateStateDriftReasons.isNotEmpty() -> SafetyStatus.FAIL
            !execution.allCommandsPassed -> SafetyStatus.FAIL
            specificationEvaluation?.status == CriterionEvaluationStatus.FAIL -> SafetyStatus.FAIL
            specificationEvaluation?.status == CriterionEvaluationStatus.INCONCLUSIVE ||
                specificationEvaluation?.status == CriterionEvaluationStatus.NOT_EVALUATED -> SafetyStatus.REVIEW_REQUIRED
            safety.status == SafetyStatus.REVIEW_REQUIRED || pr.aggregateSeverity.name == "CRITICAL" -> SafetyStatus.REVIEW_REQUIRED
            else -> SafetyStatus.PASS
        }
        val verificationReasons = buildList {
            addAll(contractReasons)
            addAll(safety.reasons)
            addAll(candidateStateDriftReasons)
            specificationEvaluation?.criteria
                ?.filter { it.status != CriterionEvaluationStatus.PASS }
                ?.forEach { criterion ->
                    add("Specification criterion '${criterion.id}' is ${criterion.status}: ${criterion.reason}")
                }
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
            contract = contractResult,
            analysisInputDigest = analysisInput.digest,
            candidateStateDigest = candidateAtVerification.digest,
            postVerificationCandidateDigest = postVerificationCandidate.digest,
            candidateStateStable = candidateStateDriftReasons.isEmpty(),
            candidateStateFileCount = candidateAtVerification.fileCount,
            postVerificationFileCount = postVerificationCandidate.fileCount,
            specificationEvaluation = specificationEvaluation
        )
    }
}
