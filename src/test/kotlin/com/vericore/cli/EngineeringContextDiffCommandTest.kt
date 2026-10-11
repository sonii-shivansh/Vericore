package com.vericore.cli

import com.vericore.core.intelligence.ContextDiffChange
import com.vericore.core.intelligence.ContextDiffSummary
import com.vericore.core.intelligence.EngineeringContextDiff
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineeringContextDiffCommandTest {
    @Test
    fun rendersSummaryAndChangesThroughSharedOutputContract() {
        val diff = EngineeringContextDiff(
            schemaVersion = "1.0",
            beforeCommit = "before123",
            afterCommit = "after456",
            summary = ContextDiffSummary(2, 1, 3, 8),
            changes = listOf(
                ContextDiffChange("ADDED", "src/New.kt"),
                ContextDiffChange("MODIFIED", "src/Main.kt")
            )
        )

        assertEquals(
            """🧭 Engineering Context Diff

Summary
  Added: 2
  Removed: 1
  Modified: 3
  Unchanged: 8

Changes
  ADDED: src/New.kt
  MODIFIED: src/Main.kt""",
            renderEngineeringContextDiffSummary(diff)
        )
    }

    @Test
    fun includesArtifactPathAndLimitsDisplayedChangesToThirty() {
        val changes = (1..32).map { ContextDiffChange("MODIFIED", "src/File$it.kt") }
        val diff = EngineeringContextDiff(
            schemaVersion = "1.0",
            beforeCommit = null,
            afterCommit = null,
            summary = ContextDiffSummary(0, 0, 32, 0),
            changes = changes
        )

        val rendered = renderEngineeringContextDiffSummary(diff, "output/engineering-context-diff.json")
        assertEquals(true, rendered.contains("Report\n  Context diff: output/engineering-context-diff.json"))
        assertEquals(true, rendered.contains("MODIFIED: src/File30.kt"))
        assertEquals(false, rendered.contains("MODIFIED: src/File31.kt"))
        assertEquals(false, rendered.contains("MODIFIED: src/File32.kt"))
    }
}
