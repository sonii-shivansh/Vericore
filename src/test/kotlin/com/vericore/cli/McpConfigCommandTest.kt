package com.vericore.cli

import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(expected, result.output.trim())
        val parsed = Json.parseToJsonElement(result.output)
        val server = parsed.jsonObject["mcpServers"]!!.jsonObject["vericore"]!!.jsonObject
        assertEquals("vericore", server["command"]?.toString()?.trim('"'))
        assertEquals("""["mcp"]""", server["args"]?.toString())
    }
}
