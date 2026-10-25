package com.vericore.core.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentWorkflowEnvelopeTest {
    private fun contract(context: AgentContext? = null) = AgentChangeContract(
        changeSummary = "Update service safely",
        repository = "/tmp/example-repo",
        preparedHead = "abc123",
        plannedPaths = listOf("src/App.kt"),
        expectedComponents = listOf("src/App.kt"),
        verificationCommands = listOf("./gradlew test"),
        evidenceIds = listOf("e-1"),
        fingerprint = "a".repeat(64),
        agentContext = context
    )

    @Test
    fun `prepared envelope carries stable contract identity and context`() {
        val context = AgentContext.create("cursor", agentId = "agent-1", eventId = "event-7", source = "cli")
        val envelope = AgentWorkflowEnvelope.prepared(contract(context))

        assertEquals("1.0", envelope.schemaVersion)
        assertEquals(AgentWorkflowEventType.CHANGE_PREPARED, envelope.eventType)
        assertEquals("event-7", envelope.eventId)
        assertEquals(context, envelope.agentContext)
        assertEquals("a".repeat(64), envelope.contractFingerprint)
        assertNull(envelope.status)
        assertNull(envelope.observedHead)

        val json = Json.encodeToJsonElement(AgentWorkflowEnvelope.serializer(), envelope).jsonObject
        assertEquals("change.prepared", json["eventType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `verified envelope derives status and paths from receipt`() {
        val context = AgentContext.create("codex", sessionId = "session-2", eventId = "event-8", source = "mcp")
        val receipt = VerificationReceipt(
            repository = "/tmp/example-repo",
            preparedHead = "abc123",
            verifiedHead = "def456",
            contractFingerprint = "b".repeat(64),
            status = SafetyStatus.PASS,
            changedPaths = listOf("src/App.kt"),
            plannedPaths = listOf("src/App.kt"),
            unexpectedPaths = emptyList(),
            deletedPaths = emptyList(),
            verificationCommands = listOf("./gradlew test"),
            verification = VerificationExecutionResult(),
            safetyReasons = emptyList(),
            verificationReasons = emptyList(),
            analysisInputDigest = "c".repeat(64),
            candidateStateDigest = "d".repeat(64),
            postVerificationCandidateDigest = "d".repeat(64),
            candidateStateStable = true,
            candidateStateFileCount = 1,
            postVerificationFileCount = 1,
            agentContext = context
        )

        val envelope = AgentWorkflowEnvelope.verified(receipt)
        assertEquals(AgentWorkflowEventType.CHANGE_VERIFIED, envelope.eventType)
        assertEquals("event-8", envelope.eventId)
        assertEquals("def456", envelope.observedHead)
        assertEquals(SafetyStatus.PASS, envelope.status)
        assertEquals("1.5", envelope.receiptSchemaVersion)
        assertEquals(listOf("src/App.kt"), envelope.changedPaths)
    }

    @Test
    fun `envelope rejects invalid fingerprints and mismatched event identity`() {
        assertFailsWith<IllegalArgumentException> {
            AgentWorkflowEnvelope(
                eventType = AgentWorkflowEventType.CHANGE_PREPARED,
                repository = "/tmp/example-repo",
                contractFingerprint = "not-a-digest",
                preparedHead = "abc123"
            )
        }
        val context = AgentContext.create("cursor", eventId = "context-event", source = "cli")
        assertFailsWith<IllegalArgumentException> {
            AgentWorkflowEnvelope(
                eventType = AgentWorkflowEventType.CHANGE_PREPARED,
                repository = "/tmp/example-repo",
                contractFingerprint = "a".repeat(64),
                preparedHead = "abc123",
                agentContext = context,
                eventId = "different-event"
            )
        }
    }

    @Test
    fun `prepared envelope serialization is versioned and portable`() {
        val envelope = AgentWorkflowEnvelope.prepared(contract())
        val decoded = Json.decodeFromString<AgentWorkflowEnvelope>(Json.encodeToString(envelope))
        assertEquals(envelope, decoded)
    }
}
