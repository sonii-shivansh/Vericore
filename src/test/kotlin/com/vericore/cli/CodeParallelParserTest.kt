package com.vericore.cli

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CodeParallelParserTest {
    @Test
    fun `parser processes many files through bounded dispatcher`() = runBlocking {
        val root = Files.createTempDirectory("vericore-parallel-parser-").toFile()
        try {
            val files = (1..250).map { index ->
                root.resolve("Class$index.kt").apply {
                    writeText("package fixture\nclass Class$index")
                }
            }

            val parser = CodeParallelParser()
            val parsed = parser.parseFiles(files)

            assertEquals(250, parsed.size)
            assertEquals(0, parser.lastWarningCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `parser concurrency limits are bounded`() {
        assertTrue(CodeParallelParser.DEFAULT_PARSER_CONCURRENCY in 1..CodeParallelParser.MAX_PARSER_CONCURRENCY)
        assertTrue(CodeParallelParser.MAX_PARSER_CONCURRENCY <= 32)
    }
}
