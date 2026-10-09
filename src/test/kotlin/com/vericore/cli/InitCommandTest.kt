package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InitCommandTest {
    @Test
    fun initializesGitRepositoryWithoutRequiringAi() {
        val root = Files.createTempDirectory("vericore-init").toFile()
        root.resolve(".git").mkdirs()
        val result = InitCommand().test("--path ${cliPath(root)}")
        assertEquals(0, result.statusCode)
        assertTrue(root.resolve(".vericore.json").isFile)
        assertTrue(root.resolve(".vericore/sessions").isDirectory)
        assertTrue(root.resolve(".vericore/reports").isDirectory)
        assertTrue(result.stdout.contains("PROJECT INITIALIZED"))
        assertTrue(result.stdout.contains("vericore setup"))
    }

    @Test
    fun refusesNonGitDirectory() {
        val root = Files.createTempDirectory("vericore-no-git").toFile()
        val error = assertFailsWith<IllegalArgumentException> {
            InitCommand().test("--path ${cliPath(root)}")
        }
        assertTrue(error.message.orEmpty().contains("Not a Git repository"))
    }

    @Test
    fun jsonOutputUsesProductResultEnvelope() {
        val root = Files.createTempDirectory("vericore-init-json").toFile()
        root.resolve(".git").mkdirs()
        val result = InitCommand().test("--path ${cliPath(root)} --json")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"schemaVersion\": \"1.0\""))
        assertTrue(result.stdout.contains("\"command\": \"init\""))
        assertTrue(result.stdout.contains("\"status\": \"PASS\""))
        assertTrue(result.stdout.contains("\"nextStep\""))
    }

    private fun cliPath(file: java.io.File): String =
        file.absolutePath.replace(java.io.File.separatorChar, '/')
}
