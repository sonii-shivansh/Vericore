package com.vericore.core.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Lifecycle events emitted by CLI and MCP around the same immutable change contract. */
@Serializable
enum class AgentWorkflowEventType {
    @SerialName("change.prepared")
    CHANGE_PREPARED,

    @SerialName("change.verified")
    CHANGE_VERIFIED
}

/**
 * Stable, versioned lifecycle summary shared by agent adapters.
 *
 * This is correlation metadata, not an authorization decision or a replacement for the
 * full contract/verification receipt. The file written by an integration represents the
 * latest lifecycle state; it is not an append-only event log.
 */
@Serializable
data class AgentWorkflowEnvelope(
    val schemaVersion: String = "1.0",
    val eventType: AgentWorkflowEventType,
    val repository: String,
    val contractFingerprint: String,
    val preparedHead: String,
    val plannedPaths: List<String> = emptyList(),
    val changedPaths: List<String> = emptyList(),
    val agentContext: AgentContext? = null,
    val eventId: String? = agentContext?.eventId,
    val observedHead: String? = null,
    val status: SafetyStatus? = null,
    val receiptSchemaVersion: String? = null
) {
    init {
        require(schemaVersion == "1.0") { "Unsupported agent workflow envelope schema version." }
        require(repository.isNotBlank()) { "Envelope repository must be non-empty." }
        require(contractFingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Envelope contract fingerprint must be a lowercase SHA-256 digest."
        }
        when (eventType) {
            AgentWorkflowEventType.CHANGE_PREPARED -> require(
                observedHead == null && status == null && receiptSchemaVersion == null && changedPaths.isEmpty()
            ) { "Prepared envelopes must not contain verification-result fields." }
            AgentWorkflowEventType.CHANGE_VERIFIED -> require(
                status != null && receiptSchemaVersion?.isNotBlank() == true
            ) { "Verified envelopes require a status and receipt schema version." }
        }
        require(eventId == agentContext?.eventId) { "Envelope eventId must match agentContext.eventId." }
    }

    companion object {
        fun prepared(contract: AgentChangeContract): AgentWorkflowEnvelope = AgentWorkflowEnvelope(
            eventType = AgentWorkflowEventType.CHANGE_PREPARED,
            repository = contract.repository,
            contractFingerprint = contract.fingerprint,
            preparedHead = contract.preparedHead,
            plannedPaths = contract.plannedPaths,
            agentContext = contract.agentContext
        )

        fun verified(receipt: VerificationReceipt): AgentWorkflowEnvelope = AgentWorkflowEnvelope(
            eventType = AgentWorkflowEventType.CHANGE_VERIFIED,
            repository = receipt.repository,
            contractFingerprint = receipt.contractFingerprint,
            preparedHead = receipt.preparedHead,
            plannedPaths = receipt.plannedPaths,
            changedPaths = receipt.changedPaths,
            agentContext = receipt.agentContext,
            observedHead = receipt.verifiedHead.takeIf { it.isNotBlank() },
            status = receipt.status,
            receiptSchemaVersion = receipt.schemaVersion
        )
    }
}
