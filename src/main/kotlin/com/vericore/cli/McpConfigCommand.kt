package com.vericore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

/** Emits or writes a client-ready stdio MCP server configuration for Vericore. */
class McpConfigCommand : CliktCommand(
    name = "mcp-config",
    help = "Print or write a client-ready MCP stdio server configuration"
) {
    private val write by option("--write", help = "Write the project-level .mcp.json instead of printing it").flag()
    private val force by option("--force", help = "Allow replacing an existing .mcp.json when used with --write").flag()

    override fun run() {
        val config = """
            {
              "mcpServers": {
                "vericore": {
                  "command": "vericore",
                  "args": ["mcp"]
                }
              }
            }
        """.trimIndent()

        if (!write) {
            echo(config)
            return
        }

        val target = File(".mcp.json")
        require(!target.exists() || force) {
            "${target.path} already exists. Use --force only if you intend to replace it."
        }
        target.writeText("$config${System.lineSeparator()}")
        echo("Wrote ${target.path}. MCP clients can use the project-level configuration.")
    }
}
