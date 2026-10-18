package com.vericore.core.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationExecutionResultTest {
    @Test
    fun `timed out command was started but execution is incomplete`() {
        val result = VerificationExecutionResult(
            commands = listOf(
                VerificationCommandResult(
                    command = "./gradlew test",
                    executed = true,
                    exitCode = null,
                    timedOut = true
                )
            )
        )

        assertEquals(1, result.commandsExecuted)
        assertTrue(result.allCommandsExecuted)
        assertFalse(result.executionComplete)
        assertFalse(result.allCommandsPassed)
    }

    @Test
    fun `completed command with nonzero exit is complete but not passed`() {
        val result = VerificationExecutionResult(
            commands = listOf(
                VerificationCommandResult(
                    command = "./gradlew test",
                    executed = true,
                    exitCode = 1,
                    timedOut = false
                )
            )
        )

        assertTrue(result.allCommandsExecuted)
        assertTrue(result.executionComplete)
        assertFalse(result.allCommandsPassed)
    }

    @Test
    fun `command rejected before execution is incomplete`() {
        val result = VerificationExecutionResult(
            commands = listOf(
                VerificationCommandResult(
                    command = "./gradlew test",
                    executed = false,
                    error = "rejected"
                )
            )
        )

        assertEquals(0, result.commandsExecuted)
        assertFalse(result.allCommandsExecuted)
        assertFalse(result.executionComplete)
        assertFalse(result.allCommandsPassed)
    }
}
