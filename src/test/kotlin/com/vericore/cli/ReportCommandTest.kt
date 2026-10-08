package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportCommandTest {
    @Test
    fun exposesProductLevelHelp() {
        val result = ReportCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("durable HTML engineering report"))
        assertTrue(result.stdout.contains("--session"))
    }
}