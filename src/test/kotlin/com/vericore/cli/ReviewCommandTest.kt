package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.intelligence.ChangeSummary
import com.vericore.core.intelligence.FindingSeverity
import com.vericore.core.intelligence.PRFinding
import com.vericore.core.intelligence.PRIntelligenceResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewCommandTest {
    @Test
    fun exposesReviewHelp() {
        val result = ReviewCommand().test("--help")
        assertEquals(0, result.statusCode)
        kotlin.test.assertTrue(result.stdout.contains("Review a Git change"))
        kotlin.test.assertTrue(result.stdout.contains("--base"))
        kotlin.test.assertTrue(result.stdout.contains("--json"))
        kotlin.test.assertTrue(result.stdout.contains("--ai"))
    }

    @Test
    fun rendersDeterministicFindingsAndReportPathThroughSharedOutput() {
        val result = sampleResult(
            findings = listOf(
                PRFinding(
                    ruleId = "ARCH_CROSS_PACKAGE",
                    severity = FindingSeverity.MEDIUM,
                    paths = listOf("src/main/Foo.kt", "src/main/Bar.kt"),
                    evidence = mapOf("crossPackageImpacts" to "1"),
                    reason = "The change affects code outside the package set containing the changed files."
                )
            )
        )

        assertEquals(
            """🔎 Vericore Review

Report
  Review JSON: /repo/output/review.json

Summary
  Changed files: 2
  Lines: +15 / -4
  Impacted files: 3
  Cross-package impacts: 1
  Test candidates: 1
  Risk: MEDIUM

Findings
  [MEDIUM] ARCH_CROSS_PACKAGE
    The change affects code outside the package set containing the changed files.
    Files: src/main/Foo.kt, src/main/Bar.kt

Next step
  Use `vericore verify` after prepare when a change contract exists.""",
            renderReviewSummary(result, outputPath = "/repo/output/review.json")
        )
    }

    @Test
    fun explainsWhenThereAreNoDeterministicFindings() {
        assertEquals(
            """🔎 Vericore Review

Summary
  Changed files: 0
  Lines: +0 / -0
  Impacted files: 0
  Cross-package impacts: 0
  Test candidates: 0
  Risk: INFO

Findings
  No deterministic review findings.

Next step
  Use `vericore verify` after prepare when a change contract exists.""",
            renderReviewSummary(sampleResult(findings = emptyList()))
        )
    }

    private fun sampleResult(findings: List<PRFinding>) = PRIntelligenceResult(
        changeSummary = ChangeSummary(
            filesChanged = if (findings.isEmpty()) 0 else 2,
            filesAdded = 1,
            filesModified = 1,
            filesDeleted = 0,
            filesRenamed = 0,
            filesCopied = 0,
            additions = if (findings.isEmpty()) 0 else 15,
            deletions = if (findings.isEmpty()) 0 else 4
        ),
        impactedFiles = if (findings.isEmpty()) 0 else 3,
        impactedPackages = if (findings.isEmpty()) 0 else 2,
        crossPackageImpacts = if (findings.isEmpty()) 0 else 1,
        testCandidates = if (findings.isEmpty()) emptyList() else listOf("src/test/FooTest.kt"),
        findings = findings,
        aggregateSeverity = if (findings.isEmpty()) FindingSeverity.INFO else FindingSeverity.MEDIUM
    )
}
