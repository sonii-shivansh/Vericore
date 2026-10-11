package com.vericore.cli

import com.vericore.core.intelligence.ArchitectureFinding
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.intelligence.ArchitectureSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureCommandTest {
    @Test
    fun rendersSummaryThroughSharedHumanOutputContract() {
        val result = ArchitectureIntelligenceResult(
            schemaVersion = "1.1",
            summary = ArchitectureSummary(12, 18, 1, 0, 3, 2),
            findings = listOf(
                ArchitectureFinding(
                    ruleId = "ARCH-LAYER-001",
                    severity = "HIGH",
                    source = "src/main.kt",
                    target = "src/domain.kt",
                    relationship = "dependency",
                    evidence = "application layer depends on domain layer"
                )
            ),
            cycles = emptyList(),
            layers = emptyMap()
        )

        assertEquals(
            """🏛️ Architecture Intelligence

Summary
  Files analyzed: 12
  Dependency edges: 18
  Findings: 1
  Cycles: 0
  Cross-layer dependencies: 3
  High-coupling files: 2

Top findings
  HIGH ARCH-LAYER-001: src/main.kt -> src/domain.kt""",
            renderArchitectureSummary(result)
        )
    }

    @Test
    fun includesArtifactPathOnlyWhenJsonArtifactIsWritten() {
        val result = ArchitectureIntelligenceResult(
            schemaVersion = "1.1",
            summary = ArchitectureSummary(0, 0, 0, 0, 0, 0),
            findings = emptyList(),
            cycles = emptyList(),
            layers = emptyMap()
        )

        assertEquals(
            """🏛️ Architecture Intelligence

Report
  Architecture report: /repo/output/architecture.json

Summary
  Files analyzed: 0
  Dependency edges: 0
  Findings: 0
  Cycles: 0
  Cross-layer dependencies: 0
  High-coupling files: 0""",
            renderArchitectureSummary(result, "/repo/output/architecture.json")
        )
    }
}
