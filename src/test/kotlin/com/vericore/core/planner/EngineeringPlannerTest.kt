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
    fun `plan records canonical repository and verification commands`() {
        val repo = Files.createTempDirectory("vericore-plan-test").toFile()
        try {
            repo.resolve("pom.xml").writeText("<project/>")
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
            assertTrue(plan.verificationCommands.none { "./gradlew" in it })
            assertTrue(plan.verificationCommands.all { "mvn" in it })
            assertEquals(repo.canonicalPath, com.vericore.core.workflow.AgentChangeContract.fromPlan(plan).repository)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `nested Maven project generates Maven commands without Gradle assumptions`() {
        val repo = Files.createTempDirectory("vericore-plan-maven-").toFile()
        try {
            repo.resolve("java/pom.xml").apply {
                parentFile.mkdirs()
                writeText("<project/>")
            }
            val plan = planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Update PDF parser",
                    changedPaths = listOf("java/src/Main.java"),
                    evidence = GroundedEvidence(citations = emptyList()),
                    repositoryPath = repo.path
                )
            )
            assertTrue(plan.verificationCommands.all { "mvn" in it })
            assertTrue(plan.verificationCommands.all { "-f 'java/pom.xml'" in it })
            assertTrue(plan.verificationCommands.none { "gradle" in it.lowercase() })
            assertTrue(plan.uncertainties.any { it.contains("no Maven wrapper") })
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `no supported build tool produces no invented verification command`() {
        val repo = Files.createTempDirectory("vericore-plan-unknown-").toFile()
        try {
            val plan = planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Document change",
                    evidence = GroundedEvidence(citations = emptyList()),
                    repositoryPath = repo.path
                )
            )
            assertTrue(plan.verificationCommands.isEmpty())
            assertTrue(plan.uncertainties.any { it.contains("No supported Gradle or Maven build entry point") })
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
