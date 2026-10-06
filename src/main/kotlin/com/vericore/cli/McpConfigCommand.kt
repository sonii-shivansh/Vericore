package com.vericore.cli

import com.github.ajalt.clikt.core.CliktCommand

/** Emits a client-ready stdio MCP server configuration for Vericore. */
class McpConfigCommand : CliktCommand(
    name = "mcp-config",
    help = "Print a client-ready MCP stdio server configuration"
) {
    override fun run() {
        echo(
            """
            {
              "mcpServers": {
                "vericore": {
                  "command": "vericore",
                  "args": ["mcp"]
                }
              }
            }
            """.trimIndent()
        )
    }
}
