package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.RiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
    fun `fingerprint is independent of change type map insertion order`() {
        val base = AgentChangeContract.fromPlan(plan("src/App.kt"))
        val first = base.copy(expectedChangeTypes = linkedMapOf("src/App.kt" to listOf("MODIFIED"), "src/Other.kt" to listOf("ADDED")))
        val second = base.copy(expectedChangeTypes = linkedMapOf("src/Other.kt" to listOf("ADDED"), "src/App.kt" to listOf("MODIFIED")))
        assertEquals(AgentChangeContract.fingerprintFor(first), AgentChangeContract.fingerprintFor(second))
    }
}
