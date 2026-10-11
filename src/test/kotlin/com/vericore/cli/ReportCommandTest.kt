package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportCommandTest {
    @Test
    fun rendersReportSummaryThroughSharedHumanOutputContract() {
        assertEquals(
            """REPORT GENERATED

Session
  session-123

Repository
  /workspace/example

Report
  /workspace/example/.vericore/reports/session-123.html""",
            renderReportSummary(
                sessionId = "session-123",
                repositoryPath = "/workspace/example",
                reportPath = "/workspace/example/.vericore/reports/session-123.html"
            )
        )
    }

    @Test
    fun exposesProductLevelHelp() {
        val result = ReportCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("durable HTML engineering report"))
        assertTrue(result.stdout.contains("--session"))
    }
}
