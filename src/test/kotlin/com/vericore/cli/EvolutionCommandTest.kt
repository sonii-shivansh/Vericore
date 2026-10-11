package com.vericore.cli

import com.vericore.core.temporal.CodebaseSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class EvolutionCommandTest {
    @Test
    fun rendersSnapshotRowsAndNetGrowthThroughSharedOutput() {
        val snapshots = listOf(
            CodebaseSnapshot(Instant.parse("2026-01-01T12:30:00Z"), "abcdef012345", 10, 120, emptyList()),
            CodebaseSnapshot(Instant.parse("2026-02-01T12:30:00Z"), "123456789abc", 15, 180, emptyList())
        )

        assertEquals(
            """📈 Evolution Report

Snapshots
  Timestamp            | Commit  | Files | Lines
  2026-01-01 12:30 | abcdef0 |    10 | 120
  2026-02-01 12:30 | 1234567 |    15 | 180

Growth
  Net file growth: 50.0% (10 → 15 files)""",
            renderEvolutionReport(snapshots, ZoneId.of("UTC"))
        )
    }

    @Test
    fun preservesEmptyHistoryReportWithoutGrowthSection() {
        assertEquals(
            """📈 Evolution Report

Snapshots
  Timestamp            | Commit  | Files | Lines""",
            renderEvolutionReport(emptyList(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun avoidsDivisionByZeroForEmptyStartingCodebase() {
        val snapshots = listOf(
            CodebaseSnapshot(Instant.parse("2026-01-01T12:30:00Z"), "abcdef012345", 0, 0, emptyList()),
            CodebaseSnapshot(Instant.parse("2026-02-01T12:30:00Z"), "123456789abc", 4, 50, emptyList())
        )
        assertEquals(true, renderEvolutionReport(snapshots, ZoneId.of("UTC")).contains("Net file growth: 0.0% (0 → 4 files)"))
    }
}
