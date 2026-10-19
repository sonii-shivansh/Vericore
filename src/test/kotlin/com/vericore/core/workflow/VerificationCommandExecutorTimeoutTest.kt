package com.vericore.core.workflow

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationCommandExecutorTimeoutTest {
    @Test
    fun `timed out process is started but verification execution remains incomplete`() {
        val repository = Files.createTempDirectory("vericore-verification-timeout").toFile()
        try {
            val windows = System.getProperty("os.name").lowercase().contains("win")
            val wrapper = File(repository, if (windows) "gradlew.bat" else "gradlew")
            if (windows) {
                wrapper.writeText("@echo off\r\nping 127.0.0.1 -n 15 > nul\r\n")
            } else {
                wrapper.writeText("#!/bin/sh\nsleep 15\n")
                assertTrue(wrapper.setExecutable(true), "test wrapper must be executable")
            }

            val command = if (windows) {
                "cd /d \"${repository.canonicalPath}\" && gradlew.bat test"
            } else {
                "cd '${repository.canonicalPath}' && ./gradlew test"
            }
            val commandResult = VerificationCommandExecutor.execute(
                repository = repository,
                commands = listOf(command),
                timeoutSeconds = 1
            ).single()

            assertTrue(commandResult.executed, "the process should have started")
            assertTrue(commandResult.timedOut, "the wrapper should exceed the one-second timeout")
            assertNull(commandResult.exitCode, "timed-out commands must not claim an exit code")

            val result = VerificationExecutionResult(commands = listOf(commandResult))
            assertTrue(result.allCommandsExecuted)
            assertFalse(result.executionComplete)
            assertFalse(result.allCommandsPassed)
        } finally {
            repository.deleteRecursively()
        }
    }
}
