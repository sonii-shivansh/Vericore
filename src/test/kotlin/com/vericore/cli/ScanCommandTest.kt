package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScanCommandTest {
    @Test
    fun exposesProductLevelHelp() {
        val result = ScanCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Understand a repository"))
        assertTrue(result.stdout.contains("--path"))
    }

    @Test
    fun rejectsMissingRepositoryPath() {
        val root = Files.createTempDirectory("vericore-scan-missing")
        val error = assertFailsWith<IllegalArgumentException> {
            ScanCommand().test("--path ${root.resolve("does-not-exist")}")
        }
        assertTrue(error.message.orEmpty().contains("Path does not exist"))
    }
}
