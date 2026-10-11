package com.vericore.cli

import com.vericore.core.intelligence.ImpactSummary
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
