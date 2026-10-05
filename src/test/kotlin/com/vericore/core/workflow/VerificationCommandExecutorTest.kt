package com.vericore.core.workflow

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationCommandExecutorTest {
    private fun linuxFixture(): File {
        val root = Files.createTempDirectory("vericore-vcore003-").toFile()
        val wrapper = File(root, "mvnw")
        wrapper.writeText("#!/bin/sh\nexit 0\n")
        Files.setPosixFilePermissions(
            wrapper.toPath(),
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        )
        return root
    }

    @Test
    fun `executes declared command and records exit code`() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        val command = "cd '${root.canonicalPath}' && ./mvnw"

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

        assertTrue(result.executed)
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `non zero verification command produces failure evidence`() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        File(root, "mvnw").writeText("#!/bin/sh\nexit 7\n")
        Files.setPosixFilePermissions(
            File(root, "mvnw").toPath(),
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        )
        val command = "cd '${root.canonicalPath}' && ./mvnw test"

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

        assertTrue(result.executed)
        assertEquals(7, result.exitCode)
        assertFalse(result.timedOut)
    }

    @Test
    fun `unsafe command is not executed`() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        val command = "cd '${root.canonicalPath}' && ./mvnw test; touch SHOULD_NOT_EXIST"

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

        assertFalse(result.executed)
        assertFalse(File(root, "SHOULD_NOT_EXIST").exists())
    }

    @Test
    fun `timed out command is reported`() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        File(root, "mvnw").writeText("#!/bin/sh\nsleep 2\n")
        Files.setPosixFilePermissions(
            File(root, "mvnw").toPath(),
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        )
        val command = "cd '${root.canonicalPath}' && ./mvnw test"

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 1).single()

        assertTrue(result.executed)
        assertTrue(result.timedOut)
        assertEquals(null, result.exitCode)
    }
}
