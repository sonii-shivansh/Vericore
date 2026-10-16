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

            assertEquals("3.0", prepared.schemaVersion)
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
    fun `fingerprint is independent of change type map insertion order`() {
        val base = AgentChangeContract.fromPlan(plan("src/App.kt"))
        val first = base.copy(expectedChangeTypes = linkedMapOf("src/App.kt" to listOf("MODIFIED"), "src/Other.kt" to listOf("ADDED")))
        val second = base.copy(expectedChangeTypes = linkedMapOf("src/Other.kt" to listOf("ADDED"), "src/App.kt" to listOf("MODIFIED")))
        assertEquals(AgentChangeContract.fingerprintFor(first), AgentChangeContract.fingerprintFor(second))
    }
}
