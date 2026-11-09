package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.RiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgentChangeContractTest {
    private fun plan(path: String, repository: String = "") = EngineeringPlan(
        changeSummary = "Update service behavior",
        repository = repository,
        affectedComponents = listOf(path),
        plannedPaths = listOf(path),
        concerns = listOf("Review architecture evidence before implementation."),
        riskLevel = RiskLevel.MEDIUM,
        steps = emptyList(),
        verificationCommands = listOf("./gradlew test"),
        evidenceIds = listOf("e-1"),
        uncertainties = emptyList()
    )

    @Test
    fun `same plan produces stable fingerprint`() {
        assertEquals(AgentChangeContract.fingerprintFor(plan("src/App.kt")), AgentChangeContract.fingerprintFor(plan("src/App.kt")))
    }

    @Test
    fun `different planned path changes fingerprint`() {
        assertNotEquals(AgentChangeContract.fingerprintFor(plan("src/App.kt")), AgentChangeContract.fingerprintFor(plan("src/Other.kt")))
    }

    @Test
    fun `contract captures plan scope and verification`() {
        val contract = AgentChangeContract.fromPlan(plan("src/App.kt", "/tmp/example-repo"))
        assertEquals(listOf("src/App.kt"), contract.plannedPaths)
        assertEquals(listOf("./gradlew test"), contract.verificationCommands)
        assertEquals(java.io.File("/tmp/example-repo").canonicalPath, contract.repository)
        assertEquals(64, contract.fingerprint.length)
    }

    @Test
    fun `repository scope changes fingerprint`() {
        val first = AgentChangeContract.fromPlan(plan("src/App.kt", "/tmp/repo-one"))
        val second = AgentChangeContract.fromPlan(plan("src/App.kt", "/tmp/repo-two"))
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `fingerprint covers expected change types`() {
        val base = AgentChangeContract.fromPlan(plan("src/App.kt"))
        val changed = base.copy(expectedChangeTypes = mapOf("src/App.kt" to listOf("MODIFIED")))
        assertNotEquals(AgentChangeContract.fingerprintFor(base), AgentChangeContract.fingerprintFor(changed))
    }

    @Test
    fun `structured specification changes contract fingerprint`() {
        val base = plan("src/App.kt")
        val specified = base.copy(
            specification = ChangeSpecification(
                requiredBehaviors = listOf("Return a validated result"),
                forbiddenBehaviors = listOf("Do not log secrets"),
                protectedInvariants = listOf("Keep retries idempotent"),
                acceptanceCriteria = listOf("A malformed request returns 400"),
                requiredEvidence = listOf("Test output for malformed requests")
            )
        )

        assertNotEquals(
            AgentChangeContract.fingerprintFor(base),
            AgentChangeContract.fingerprintFor(specified)
        )
        val contract = AgentChangeContract.fromPlan(specified, "/tmp/spec-repo", "prepared-head")
        assertEquals(specified.specification, contract.specification)
        assertEquals(AgentChangeContract.fingerprintFor(contract), contract.fingerprint)
    }

    @Test
    fun `criterion evidence bindings are included in contract fingerprint`() {
        val base = plan("src/App.kt").copy(
            specification = ChangeSpecification(acceptanceCriteria = listOf("Invalid input returns 400"))
        )
        val bound = base.copy(
            specification = base.specification.copy(
                evidenceBindings = listOf(
                    com.vericore.core.planner.CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1"))
                )
            )
        )

        assertNotEquals(
            AgentChangeContract.fingerprintFor(base),
            AgentChangeContract.fingerprintFor(bound)
        )
    }

    @Test
    fun `empty specification preserves legacy contract fingerprint semantics`() {
        val base = plan("src/App.kt")
        val explicitEmpty = base.copy(specification = ChangeSpecification())
        assertEquals(
            AgentChangeContract.fingerprintFor(base),
            AgentChangeContract.fingerprintFor(explicitEmpty)
        )
    }

    @Test
    fun `fingerprint covers schema version`() {
        val base = AgentChangeContract.fromPlan(plan("src/App.kt"))
        val changed = base.copy(schemaVersion = "3.0")
        assertNotEquals(AgentChangeContract.fingerprintFor(base), AgentChangeContract.fingerprintFor(changed))
    }

    @Test
    fun `contract binds baseline digest and rejects tampered manifest entries`() {
        val root = java.nio.file.Files.createTempDirectory("contract-baseline")
        try {
            val source = root.resolve("src/App.kt")
            java.nio.file.Files.createDirectories(source.parent)
            java.nio.file.Files.writeString(source, "class App\n")
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())
            val prepared = AgentChangeContract.fromPlan(
                plan("src/App.kt", root.toFile().canonicalPath),
                root.toFile().canonicalPath,
                "prepared-head",
                baseline
            )

            assertEquals("3.2", prepared.schemaVersion)
            assertEquals(prepared.fingerprint, AgentChangeContract.fingerprintFor(prepared))
            assertTrue(baseline.isValid())

            java.nio.file.Files.writeString(source, "class App { fun changed() = Unit }\n")
            val changedBaseline = CandidateStateSnapshotter.captureManifest(root.toFile())
            val changedContract = AgentChangeContract.fromPlan(
                plan("src/App.kt", root.toFile().canonicalPath),
                root.toFile().canonicalPath,
                "prepared-head",
                changedBaseline
            )
            assertNotEquals(prepared.fingerprint, changedContract.fingerprint)

            val tamperedManifest = baseline.copy(
                entries = baseline.entries.mapIndexed { index, entry ->
                    if (index == 0) entry.copy(contentDigest = "0".repeat(64)) else entry
                }
            )
            val tamperedContract = prepared.copy(baselineManifest = tamperedManifest)
            assertTrue(!tamperedManifest.isValid())
            // The fingerprint binds the manifest digest, while validation independently
            // recomputes that digest from entries so edited entries cannot be trusted.
            assertEquals(prepared.fingerprint, AgentChangeContract.fingerprintFor(tamperedContract))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `agent context is normalized versioned and bound to contract fingerprint`() {
        val root = java.nio.file.Files.createTempDirectory("agent-context")
        try {
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())
            val context = AgentContext.create(" Cursor ", " user-42 ", " session-abc ", " event-123 ", "cli")
            assertEquals("cursor", context.provider)
            assertEquals("user-42", context.agentId)
            assertEquals("session-abc", context.sessionId)
            assertEquals("event-123", context.eventId)
            val base = AgentChangeContract.fromPlan(plan("src/App.kt", root.toFile().canonicalPath), root.toFile().canonicalPath, "prepared-head", baseline)
            val withContext = AgentChangeContract.fromPlan(plan("src/App.kt", root.toFile().canonicalPath), root.toFile().canonicalPath, "prepared-head", baseline, context)
            assertEquals("3.2", base.schemaVersion)
            assertEquals("3.2", withContext.schemaVersion)
            assertEquals(context, withContext.agentContext)
            assertEquals(withContext.fingerprint, AgentChangeContract.fingerprintFor(withContext))
            assertNotEquals(base.fingerprint, withContext.fingerprint)
            assertNotEquals(withContext.fingerprint, AgentChangeContract.fingerprintFor(withContext.copy(agentContext = context.copy(sessionId = "different-session"))))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy schema versions produce stable historical fingerprints with sorted commands`() {
        val root = java.nio.file.Files.createTempDirectory("legacy-contract")
        try {
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())
            val planWithUnsortedCommands = plan("src/App.kt", root.toFile().canonicalPath).copy(
                verificationCommands = listOf("mvn test", "mvn clean")
            )
            val legacy30 = AgentChangeContract.fromPlan(
                planWithUnsortedCommands,
                root.toFile().canonicalPath,
                "prepared-head",
                baseline,
                schemaVersionOverride = "3.0"
            )
            assertEquals("3.0", legacy30.schemaVersion)
            assertEquals(listOf("mvn clean", "mvn test"), legacy30.verificationCommands)
            assertEquals(legacy30.fingerprint, AgentChangeContract.fingerprintFor(legacy30))

            val context = AgentContext.create("cursor", "user-1", "session-1", "event-1", "cli")
            val legacy31 = AgentChangeContract.fromPlan(
                planWithUnsortedCommands,
                root.toFile().canonicalPath,
                "prepared-head",
                baseline,
                context,
                schemaVersionOverride = "3.1"
            )
            assertEquals("3.1", legacy31.schemaVersion)
            assertEquals(listOf("mvn clean", "mvn test"), legacy31.verificationCommands)
            assertEquals(legacy31.fingerprint, AgentChangeContract.fingerprintFor(legacy31))
            assertNotEquals(legacy30.fingerprint, legacy31.fingerprint)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `schema 3_2 fingerprint distinguishes newline-joined commands from multi-command lists`() {
        val root = java.nio.file.Files.createTempDirectory("newline-collision")
        try {
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())
            val planTwoCommands = plan("src/App.kt", root.toFile().canonicalPath).copy(
                verificationCommands = listOf("mvn test", "mvn package")
            )
            val contractTwoCommands = AgentChangeContract.fromPlan(
                planTwoCommands,
                root.toFile().canonicalPath,
                "prepared-head",
                baseline
            )
            assertEquals("3.2", contractTwoCommands.schemaVersion)

            val planSingleMergedCommand = plan("src/App.kt", root.toFile().canonicalPath).copy(
                verificationCommands = listOf("mvn test\nmvn package")
            )
            val contractSingleMergedCommand = AgentChangeContract.fromPlan(
                planSingleMergedCommand,
                root.toFile().canonicalPath,
                "prepared-head",
                baseline
            )
            assertEquals("3.2", contractSingleMergedCommand.schemaVersion)

            assertNotEquals(
                contractTwoCommands.fingerprint,
                contractSingleMergedCommand.fingerprint,
                "Schema 3.2 must not encode [A, B] and [A + '\\n' + B] identically"
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `agent context rejects control characters and unknown source values`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AgentContext.create("cursor", sessionId = "session\nforged", source = "cli")
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AgentContext.create("cursor", source = "untrusted-claimed-source")
        }
    }

    @Test
    fun `fingerprint is independent of change type map insertion order`() {
        val base = AgentChangeContract.fromPlan(plan("src/App.kt"))
        val first = base.copy(expectedChangeTypes = linkedMapOf("src/App.kt" to listOf("MODIFIED"), "src/Other.kt" to listOf("ADDED")))
        val second = base.copy(expectedChangeTypes = linkedMapOf("src/Other.kt" to listOf("ADDED"), "src/App.kt" to listOf("MODIFIED")))
        assertEquals(AgentChangeContract.fingerprintFor(first), AgentChangeContract.fingerprintFor(second))
    }
}
