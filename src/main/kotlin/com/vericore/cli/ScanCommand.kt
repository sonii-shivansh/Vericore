package com.vericore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

/** Product-level repository understanding command. */
class ScanCommand : CliktCommand(
    name = "scan"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Understand a repository and generate engineering intelligence"
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val noCache by option("--no-cache").flag()
    private val clearCache by option("--clear-cache").flag()
    private val noSnapshot by option("--no-snapshot").flag()
    private val verbose by option("--verbose", "-v").flag()
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save JSON output")

    override fun run() {
        runAnalysisEngine(
            commandIdentity = "scan",
            path = path,
            noCache = noCache,
            clearCache = clearCache,
            noSnapshot = noSnapshot,
            verbose = verbose,
            format = format,
            jsonOutput = jsonOutput,
            output = output,
            echo = { echo(it) }
        )
    }
}
