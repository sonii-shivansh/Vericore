package com.vericore.server

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PathSecurityTest {
    @Test
    fun `rejects sibling path with allowed prefix`() {
        // The process working directory is an allowed root by default. Create a real
        // sibling of that root so the test verifies Path.startsWith semantics rather
        // than accidentally testing another directory under the allowed root.
        val allowedRoot = File(System.getProperty("user.dir")).canonicalFile
        val sibling = File(allowedRoot.parentFile, allowedRoot.name + "-attacker")
        check(sibling.mkdirs() || sibling.isDirectory) { "Could not create test directory" }
        try {
            assertNull(sanitizePath(sibling.canonicalPath))
        } finally {
            sibling.deleteRecursively()
        }
    }

    @Test
    fun `accepts a real directory inside the temporary workspace`() {
        val directory = createTempDirectory("codecontext-safe").toFile()
        try {
            assertNotNull(sanitizePath(directory.absolutePath))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `filesystem root is never treated as an allowed repository root`() {
        val roots = resolveSafeAllowedRoots(listOf(File.separator))
        assertTrue(roots.none { it.parent == null })
    }

}
