package com.vericore.cli

import com.vericore.core.intelligence.ChangeSummary
import com.vericore.core.intelligence.FindingSeverity
import com.vericore.core.intelligence.PRFinding
import com.vericore.core.intelligence.PRIntelligenceResult
import kotlin.test.Test
import kotlin.test.assertEquals

class PRIntelligenceCommandTest {
    @Test
    fun rendersSummaryThroughSharedHumanOutputContract() {
        val result = PRIntelligenceResult(
            changeSummary = ChangeSummary(
                filesChanged = 2,
                filesAdded = 1,
                filesModified = 1,
                filesDeleted = 0,
                filesRenamed = 0,
                filesCopied = 0,
                additions = 15,
                deletions = 4
            ),
            impactedFiles = 3,
            impactedPackages = 2,
            crossPackageImpacts = 1,
            testCandidates = listOf("src/test/FooTest.kt"),
            findings = listOf(
                PRFinding(
                    ruleId = "ARCH_CROSS_PACKAGE",
                    severity = FindingSeverity.MEDIUM,
                    paths = listOf("src/main/Foo.kt"),
                    evidence = mapOf("crossPackageImpacts" to "1"),
                    reason = "The change affects code outside the package set containing the changed files."
                )
            ),
            aggregateSeverity = FindingSeverity.MEDIUM
        )

        assertEquals(
            """🧭 PR Intelligence

Summary
  Changed files: 2
  Lines: +15 / -4
  Impacted files: 3
  Cross-package impacts: 1
  Test candidates: 1
  Aggregate severity: MEDIUM

Findings
  [MEDIUM] ARCH_CROSS_PACKAGE: The change affects code outside the package set containing the changed files.""",
            renderPRIntelligenceSummary(result)
        )
        assertEquals(
            """🧭 PR Intelligence

Report
  PR Intelligence: /repo/output/pr-intelligence.json

Summary
  Changed files: 2
  Lines: +15 / -4
  Impacted files: 3
  Cross-package impacts: 1
  Test candidates: 1
  Aggregate severity: MEDIUM

Findings
  [MEDIUM] ARCH_CROSS_PACKAGE: The change affects code outside the package set containing the changed files.""",
            renderPRIntelligenceSummary(result, "/repo/output/pr-intelligence.json")
        )
    }

    @Test
    fun omitsFindingsSectionWhenThereAreNoFindings() {
        val result = PRIntelligenceResult(
            changeSummary = ChangeSummary(
                filesChanged = 0,
                filesAdded = 0,
                filesModified = 0,
                filesDeleted = 0,
                filesRenamed = 0,
                filesCopied = 0,
                additions = 0,
                deletions = 0
            ),
            impactedFiles = 0,
            impactedPackages = 0,
            crossPackageImpacts = 0,
            testCandidates = emptyList(),
            findings = emptyList(),
            aggregateSeverity = FindingSeverity.INFO
        )

        assertEquals(
            """🧭 PR Intelligence

Summary
  Changed files: 0
  Lines: +0 / -0
  Impacted files: 0
  Cross-package impacts: 0
  Test candidates: 0
  Aggregate severity: INFO""",
            renderPRIntelligenceSummary(result)
        )
    }
}
