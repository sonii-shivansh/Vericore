package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class McpConfigCommandTest {
    @Test
    fun mcpConfigEmitsClientReadyStdioConfiguration() {
        val result = McpConfigCommand().test("")
        val expected = """
            {
              "mcpServers": {
                "vericore": {
                  "command": "vericore",
                  "args": ["mcp"]
                }
              }
            }
        """.trimIndent()

        assertEquals(expected, result.stdout.trim())
        val parsed = Json.parseToJsonElement(result.stdout)
        val server = parsed.jsonObject["mcpServers"]!!.jsonObject["vericore"]!!.jsonObject
        assertEquals("vericore", server["command"]?.toString()?.trim('"'))
        assertEquals("""["mcp"]""", server["args"]?.toString())
    }
    @Test
    fun writeCreatesProjectMcpConfigAndDoesNotOverwriteByDefault() {
        val temp = Files.createTempDirectory("vericore-mcp-config").toFile()
        val previous = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", temp.absolutePath)
            val result = McpConfigCommand().test("--write")
            val target = temp.resolve(".mcp.json")
            assertTrue(target.isFile)
            assertTrue(target.readText().contains(""""command": "vericore""""))
            assertTrue(result.stdout.contains("Wrote"))
            assertFalse(target.readText().contains("apiKey"))
        } finally {
            System.setProperty("user.dir", previous)
            temp.deleteRecursively()
        }
    }

}
