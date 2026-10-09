package com.vericore.core.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheManagerTest {
    @Test
    fun `default cache location is canonical Vericore state`() {
        assertEquals(".vericore/cache", CacheManager.DEFAULT_CACHE_DIR)
    }

    @Test
    fun `repository cache is stored under the analyzed repository`() {
        val root = Files.createTempDirectory("vericore-cache-root").toFile()
        try {
            CacheManager.forRepository(root)
            assertTrue(root.resolve(CacheManager.DEFAULT_CACHE_DIR).isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
