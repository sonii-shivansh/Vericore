package com.vericore.cli

import com.vericore.core.intelligence.ArchitectureDriftChange
import com.vericore.core.intelligence.ArchitectureDriftResult
import com.vericore.core.intelligence.ArchitectureDriftSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureDriftCommandTest {
    @Test
    fun rendersSummaryThroughSharedHumanOutputContract() {
        val result = ArchitectureDriftResult(
            schemaVersion = "1.0",
            baselineSchemaVersion = "1.1",
            currentSchemaVersion = "1.1",
            summary = ArchitectureDriftSummary(2, 1, 1, 0, 3, 4, 2),
            changes = listOf(ArchitectureDriftChange("ADDED_EDGE", "a.kt -> b.kt", "new edge"))
        )

        assertEquals(
            """🏛️ Architecture Drift

Summary
  Added findings: 2
  Removed findings: 1
  New cycles: 1
  Removed cycles: 0
  Changed layers: 3
  Added edges: 4
  Removed edges: 2

Changes
  ADDED_EDGE: a.kt -> b.kt""",
            renderArchitectureDriftSummary(result)
        )
    }

    @Test
    fun includesArtifactPathOnlyWhenJsonArtifactIsWritten() {
        val result = ArchitectureDriftResult(
            schemaVersion = "1.0",
            baselineSchemaVersion = "1.1",
            currentSchemaVersion = "1.1",
            summary = ArchitectureDriftSummary(0, 0, 0, 0, 0),
            changes = emptyList()
        )

        assertEquals(
            """🏛️ Architecture Drift

Report
  Architecture drift report: /repo/output/architecture-drift.json

Summary
  Added findings: 0
  Removed findings: 0
  New cycles: 0
  Removed cycles: 0
  Changed layers: 0
  Added edges: 0
  Removed edges: 0""",
            renderArchitectureDriftSummary(result, "/repo/output/architecture-drift.json")
        )
    }
}
