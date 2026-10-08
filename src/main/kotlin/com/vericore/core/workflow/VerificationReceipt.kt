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
    val schemaVersion: String = "1.0",
    val repository: String,
    val preparedHead: String,
    val verifiedHead: String,
    val contractFingerprint: String,
    val status: SafetyStatus,
    val changedPaths: List<String>,
    val plannedPaths: List<String>,
    val unexpectedPaths: List<String>,
    val deletedPaths: List<String>,
    val verificationCommands: List<String>,
    val verification: VerificationExecutionResult,
    val safetyReasons: List<String>
) {
    companion object {
        fun from(result: EngineeringVerificationResult): VerificationReceipt {
            val contract = result.contract?.contract
            return VerificationReceipt(
                repository = result.repository,
                preparedHead = contract?.preparedHead.orEmpty(),
                verifiedHead = result.provenance.commit.orEmpty(),
                contractFingerprint = contract?.fingerprint.orEmpty(),
                status = result.status,
                changedPaths = result.safety.changedPaths,
                plannedPaths = result.safety.plannedPaths,
                unexpectedPaths = result.safety.unexpectedPaths,
                deletedPaths = result.safety.deletedPaths,
                verificationCommands = result.verificationCommands,
                verification = result.verification,
                safetyReasons = result.safety.reasons
            )
        }
    }
}
