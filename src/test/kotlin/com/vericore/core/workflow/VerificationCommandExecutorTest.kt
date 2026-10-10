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
    fun nestedModuleWrapperExecutesInsideTheValidatedRepositoryDirectory() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = Files.createTempDirectory("vericore-nested-module-").toFile()
        try {
            val module = File(root, "java").apply { mkdirs() }
            val wrapper = File(module, "mvnw")
            wrapper.writeText("#!/bin/sh\npwd > invoked-directory.txt\nexit 0\n")
            Files.setPosixFilePermissions(
                wrapper.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            )
            val command = "cd '" + module.canonicalPath + "' && ./mvnw -B test"

            val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

            assertTrue(result.executed)
            assertEquals(0, result.exitCode)
            assertEquals(module.canonicalPath, File(module, "invoked-directory.txt").readText().trim())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun buildWorkingDirectoryOutsideRepositoryIsRejected() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = Files.createTempDirectory("vericore-command-root-").toFile()
        val outside = Files.createTempDirectory("vericore-command-outside-").toFile()
        try {
            val marker = File(outside, "SHOULD_NOT_EXIST")
            val command = "cd '" + outside.canonicalPath + "' && ./mvnw test"

            val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

            assertFalse(result.executed)
            assertFalse(marker.exists())
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
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
    fun singleAmpersandCommandIsRejectedBeforeExecution() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        val marker = File(root, "SHOULD_NOT_EXIST")
        val command = "cd '" + root.canonicalPath + "' && ./mvnw test & touch " + marker.absolutePath

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

        assertFalse(result.executed)
        assertFalse(marker.exists())
    }

    @Test
    fun newlineSeparatedCommandIsRejectedBeforeExecution() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val root = linuxFixture()
        val marker = File(root, "SHOULD_NOT_EXIST")
        val command = "cd '" + root.canonicalPath + "' && ./mvnw test\n touch " + marker.absolutePath

        val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()

        assertFalse(result.executed)
        assertFalse(marker.exists())
    }

    @Test
    fun windowsShellSeparatorsAndLineBreaksAreRejectedBeforeExecution() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val root = Files.createTempDirectory("vericore-windows-command-guard-").toFile()
        try {
            val prefix = "cd /d \"" + root.canonicalPath + "\" && "
            val commands = listOf(
                prefix + "mvnw.cmd -B test & echo VERICORE_INJECTED_SENTINEL",
                prefix + "mvnw.cmd -B test\r\necho VERICORE_INJECTED_SENTINEL"
            )

            for (command in commands) {
                val result = VerificationCommandExecutor.execute(root, listOf(command), timeoutSeconds = 5).single()
                assertFalse(result.executed, "Rejected command must not start a shell: " + command)
            }
        } finally {
            root.deleteRecursively()
        }
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
