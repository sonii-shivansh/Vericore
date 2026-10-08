package com.vericore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

/** Product-level repository understanding command. */
class ScanCommand : CliktCommand(
    name = "scan",
    help = "Understand a repository and generate engineering intelligence"
) {
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val noCache by option("--no-cache").flag()
    private val clearCache by option("--clear-cache").flag()
    private val noSnapshot by option("--no-snapshot").flag()
    private val verbose by option("--verbose", "-v").flag()

    override fun run() {
        val args = buildList {
            add(path)
            if (noCache) add("--no-cache")
            if (clearCache) add("--clear-cache")
            if (noSnapshot) add("--no-snapshot")
            if (verbose) add("--verbose")
        }.toTypedArray()
        ImprovedAnalyzeCommand().main(args)
    }
}
