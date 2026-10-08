package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewCommandTest {
    @Test
    fun exposesReviewHelp() {
        val result = ReviewCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Review a Git change"))
        assertTrue(result.stdout.contains("--base"))
        assertTrue(result.stdout.contains("--json"))
        assertTrue(result.stdout.contains("--ai"))
    }
}