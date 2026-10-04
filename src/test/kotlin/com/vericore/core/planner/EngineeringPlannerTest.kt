package com.vericore.core.planner

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.ai.GroundedEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Files

class EngineeringPlannerTest {
    private val planner = EngineeringPlanner()

    @Test
    fun `plan is deterministic and preserves evidence ids`() {
        val evidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("hotspot.1", "hotspot", "src/PaymentService.kt", "central hotspot"),
                EvidenceCitation("architecture.summary", "architecture-summary", null, "cycles=false")
            )
        )
        val request = EngineeringPlanRequest(
            changeSummary = "Update payment validation",
            changedPaths = listOf("src/PaymentService.kt"),
            evidence = evidence
        )

        val first = planner.plan(request)
        val second = planner.plan(request)

        assertEquals(first, second)
        assertEquals(RiskLevel.MEDIUM, first.riskLevel)
        assertEquals(listOf("architecture.summary", "hotspot.1"), first.evidenceIds)
        assertTrue(first.steps.flatMap { it.evidenceIds }.all { it in first.evidenceIds })
    }

    @Test
    fun `explicit planned paths do not expand into unrelated evidence files`() {
        val evidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("file.1", "file-graph-fact", "src/PaymentService.kt", "changed service"),
                EvidenceCitation("file.2", "file-graph-fact", "src/PaymentController.kt", "dependent controller"),
                EvidenceCitation("file.3", "file-graph-fact", "src/HealthController.kt", "unrelated file")
            )
        )

        val plan = planner.plan(
            EngineeringPlanRequest(
                changeSummary = "Change payment validation",
                changedPaths = listOf("src/PaymentService.kt"),
                evidence = evidence
            )
        )

        assertEquals(listOf("src/PaymentService.kt"), plan.affectedComponents)
        assertTrue(plan.concerns.any { it.contains("supporting context", ignoreCase = true) })
    }

    @Test
    fun `plan records canonical repository and verification commands`() {
        val repo = Files.createTempDirectory("vericore-plan-test").toFile()
        try {
            val plan = planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Update payment validation",
                    changedPaths = listOf("src/PaymentService.kt"),
                    evidence = GroundedEvidence(citations = emptyList()),
                    repositoryPath = repo.path
                )
            )
            assertEquals(repo.canonicalPath, plan.repository)
            assertTrue(plan.verificationCommands.all { it.contains(repo.canonicalPath) })
            assertEquals(repo.canonicalPath, com.vericore.core.workflow.AgentChangeContract.fromPlan(plan).repository)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `unsafe changed paths are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Unsafe change",
                    changedPaths = listOf("../outside.kt"),
                    evidence = GroundedEvidence(citations = emptyList())
                )
            )
        }
    }

    @Test
    fun `empty evidence produces explicit uncertainty`() {
        val plan = planner.plan(
            EngineeringPlanRequest(
                changeSummary = "Unknown change",
                evidence = GroundedEvidence(citations = emptyList())
            )
        )

        assertEquals(RiskLevel.UNKNOWN, plan.riskLevel)
        assertTrue(plan.uncertainties.any { it.contains("No repository evidence") })
        assertTrue(plan.evidenceIds.isEmpty())
    }

    @Test
    fun `generated CodeContext and Vericore paths are excluded from affected components`() {
        val evidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("source", "source", "src/Service.kt", "source file"),
                EvidenceCitation("output", "output", "output/verify.json", "generated report"),
                EvidenceCitation("legacy-cache", "cache", ".codecontext/cache/index.json", "legacy generated cache"),
                EvidenceCitation("canonical-cache", "cache", ".vericore/cache/index.json", "canonical generated cache")
            )
        )

        val plan = planner.plan(
            EngineeringPlanRequest(
                changeSummary = "Verify generated artifacts",
                changedPaths = listOf("src/Service.kt", "output/verify.json", ".codecontext/cache/index.json", ".vericore/cache/index.json"),
                evidence = evidence
            )
        )

        assertEquals(listOf("src/Service.kt"), plan.affectedComponents)
    }

    @Test
    fun `limits are enforced`() {
        assertFailsWith<IllegalArgumentException> {
            planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "x",
                    changedPaths = List(501) { "src/$it.kt" },
                    evidence = GroundedEvidence(citations = emptyList())
                )
            )
        }
    }
}
