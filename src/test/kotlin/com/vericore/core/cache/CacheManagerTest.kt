package com.vericore.core.cache

import com.vericore.core.parser.ParsedFile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CacheManagerTest {
    @Test
    fun `default cache location is canonical Vericore state`() {
        assertEquals(".vericore/cache", CacheManager.DEFAULT_CACHE_DIR)
    }

    @Test
    fun `cache key namespace carries an explicit schema version`() {
        assertEquals("2", CacheManager.PARSE_CACHE_SCHEMA_VERSION)
    }

    @Test
    fun `parsed files round trip through an isolated cache directory`() {
        val root = Files.createTempDirectory("vericore-cache-test-").toFile()
        try {
            val source = File(root, "Example.kt").apply {
                writeText("package example\nclass Example")
            }
            val cache = CacheManager(File(root, "cache"))
            val expected = ParsedFile(
                file = source,
                packageName = "example",
                imports = listOf("kotlin.collections.List"),
                parseWarning = "fixture warning"
            )

            cache.saveParse(source, expected)
            val actual = cache.getCachedParse(source)

            assertNotNull(actual)
            assertEquals(expected.packageName, actual.packageName)
            assertEquals(expected.imports, actual.imports)
            assertEquals(expected.parseWarning, actual.parseWarning)
            assertNull(CacheManager(File(root, "other-cache")).getCachedParse(source))
        } finally {
            root.deleteRecursively()
        }
    }
}
