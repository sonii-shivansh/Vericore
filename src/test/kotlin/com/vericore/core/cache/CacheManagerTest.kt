package com.vericore.core.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheManagerTest {
    @Test
    fun `default cache location is outside a repository`() {
        val path = CacheManager.defaultCacheDirectory().canonicalPath.replace('\\', '/')
        assertFalse(path.contains("/.vericore/cache"))
        assertTrue(path.endsWith("/vericore/cache"))
    }

    @Test
    fun `repository cache is scoped deterministically`() {
        val repo = Files.createTempDirectory("vericore-cache-repo-").toFile()
        try {
            val first = CacheManager.forRepository(repo.path)
            val second = CacheManager.forRepository(repo.path)
            val firstField = first.javaClass.getDeclaredField("cacheDir").apply { isAccessible = true }.get(first) as java.io.File
            val secondField = second.javaClass.getDeclaredField("cacheDir").apply { isAccessible = true }.get(second) as java.io.File
            assertEquals(firstField.canonicalPath, secondField.canonicalPath)
            assertTrue(firstField.canonicalPath.startsWith(CacheManager.defaultCacheDirectory().canonicalPath))
        } finally {
            repo.deleteRecursively()
        }
    }
}
