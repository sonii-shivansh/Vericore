package com.vericore.core.workflow

import kotlinx.serialization.Serializable

/**
 * Compact, durable summary of a verification decision.
 *
 * This is derived from the same deterministic verification result and does not
 * create a second verification decision.
 */
@Serializable
data class VerificationReceipt(
    val schemaVersion: String = "1.3",
    val repository: String,
    val preparedHead: String,
    val verifiedHead: String,
    val contractFingerprint: String,
    val preparedBaselineDigest: String? = null,
    val baselineManifestValid: Boolean = false,
    val unplannedBaselineDriftPaths: List<String> = emptyList(),
    val status: SafetyStatus,
    val changedPaths: List<String>,
    val plannedPaths: List<String>,
    val unexpectedPaths: List<String>,
    val deletedPaths: List<String>,
    val verificationCommands: List<String>,
    val verification: VerificationExecutionResult,
    val safetyReasons: List<String>,
    val verificationReasons: List<String>,
    val analysisInputDigest: String,
    val candidateStateDigest: String,
    val postVerificationCandidateDigest: String,
    val candidateStateStable: Boolean,
    val candidateStateFileCount: Int,
    val postVerificationFileCount: Int,
    val specificationEvaluation: ChangeSpecificationEvaluation? = null,
    val junitTestReports: List<JUnitTestReportEvidence> = emptyList()
) {
    companion object {
        fun from(result: EngineeringVerificationResult): VerificationReceipt {
            val contract = result.contract?.contract
            return VerificationReceipt(
                repository = result.repository,
                preparedHead = contract?.preparedHead.orEmpty(),
                verifiedHead = result.provenance.repositoryCommit.orEmpty(),
                contractFingerprint = contract?.fingerprint.orEmpty(),
                preparedBaselineDigest = result.preparedBaselineDigest,
                baselineManifestValid = result.baselineManifestValid,
                unplannedBaselineDriftPaths = result.unplannedBaselineDriftPaths,
                status = result.status,
                changedPaths = result.safety.changedPaths,
                plannedPaths = result.safety.plannedPaths,
                unexpectedPaths = result.safety.unexpectedPaths,
                deletedPaths = result.safety.deletedPaths,
                verificationCommands = result.verificationCommands,
                verification = result.verification,
                safetyReasons = result.safety.reasons,
                verificationReasons = result.reasons,
                analysisInputDigest = result.analysisInputDigest,
                candidateStateDigest = result.candidateStateDigest,
                postVerificationCandidateDigest = result.postVerificationCandidateDigest,
                candidateStateStable = result.candidateStateStable,
                candidateStateFileCount = result.candidateStateFileCount,
                postVerificationFileCount = result.postVerificationFileCount,
                specificationEvaluation = result.specificationEvaluation,
                junitTestReports = result.junitTestReports
            )
        }
    }
}
