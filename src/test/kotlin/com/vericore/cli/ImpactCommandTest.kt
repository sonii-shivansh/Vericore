package com.vericore.cli

import com.vericore.core.intelligence.ChangeImpactResult
import com.vericore.core.intelligence.ImpactNode
import com.vericore.core.intelligence.ImpactRelationship
import com.vericore.core.intelligence.ImpactSummary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpactCommandTest {
    @Test
    fun rendersSummaryThroughSharedHumanOutputContract() {
        assertEquals(
            """🧭 Change Impact

Summary
  Changed files: 2
  Impacted files: 5
  Impacted packages: 3
  Cross-package impacts: 2
  Test candidates: 1
  Maximum dependency depth: 4""",
            renderImpactSummary(
                ImpactSummary(
                    changedFiles = 2,
                    impactedFiles = 5,
                    impactedPackages = 3,
                    crossPackageImpacts = 2,
                    testCandidates = 1,
                    maxDepth = 4
                )
            )
        )
    }

    @Test
    fun `renderImpactSummary displays unreachable test candidates with reasons`() {
        val root = File("/repo")
        val result = ChangeImpactResult(
            changedPaths = listOf("/repo/Service.kt"),
            nodes = listOf(
                ImpactNode("/repo/Service.kt", ImpactRelationship.CHANGED, 0, 100.0, listOf("explicitly changed")),
                ImpactNode("/repo/ServiceTest.kt", ImpactRelationship.TEST_CANDIDATE, -1, 50.0, listOf("test candidate matching changed file name (independent of dependency graph)"))
            ),
            summary = ImpactSummary(
                changedFiles = 1,
                impactedFiles = 0,
                impactedPackages = 0,
                crossPackageImpacts = 0,
                testCandidates = 1,
                maxDepth = 0
            ),
            testCandidates = listOf("/repo/ServiceTest.kt")
        )

        val output = renderImpactSummary(result, root)
        assertTrue(output.contains("Test candidates (1)"))
        assertTrue(output.contains("ServiceTest.kt"))
        assertTrue(output.contains("test candidate matching changed file name"))
    }
}
