package com.vericore.cli

import com.vericore.core.intelligence.ArchitectureContract
import com.vericore.core.intelligence.ArchitectureContractResult
import com.vericore.core.intelligence.ArchitectureContractViolation
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureContractCommandTest {
    @Test
    fun rendersPassedContractSummaryAndOptionalArtifactPaths() {
        val result = ArchitectureContractResult(
            schemaVersion = "1.0",
            passed = true,
            violations = emptyList(),
            checked = ArchitectureContract()
        )

        assertEquals(
            """🛡️ Architecture Contract

Report
  Architecture contract: /repo/output/architecture-contract.json

History
  Contract decision history: /repo/history.json

Summary
  Passed: true
  Findings: 4
  Cycles: 1
  Violations: 0
  Contract fingerprint: decision-123""",
            renderArchitectureContractSummary(
                result,
                findingsCount = 4,
                cyclesCount = 1,
                decisionId = "decision-123",
                outputPath = "/repo/output/architecture-contract.json",
                recordPath = "/repo/history.json"
            )
        )
    }

    @Test
    fun rendersFailureViolationsWithoutChangingDecisionResult() {
        val result = ArchitectureContractResult(
            schemaVersion = "1.0",
            passed = false,
            violations = listOf(
                ArchitectureContractViolation("CONTRACT-FINDING-LIMIT", "HIGH", "too many findings"),
                ArchitectureContractViolation("CONTRACT-RULE-001", "HIGH", "missing required rule")
            ),
            checked = ArchitectureContract()
        )

        assertEquals(
            """🛡️ Architecture Contract

Summary
  Passed: false
  Findings: 9
  Cycles: 2
  Violations: 2
  Contract fingerprint: decision-failed

Violations
  CONTRACT-FINDING-LIMIT: too many findings
  CONTRACT-RULE-001: missing required rule""",
            renderArchitectureContractSummary(
                result,
                findingsCount = 9,
                cyclesCount = 2,
                decisionId = "decision-failed"
            )
        )
        kotlin.test.assertFalse(result.passed)
    }
}
