package com.vericore.cli

import com.vericore.core.temporal.TemporalAnalyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EvolutionCommand : CliktCommand(
    name = "evolution",
    help = "Analyze codebase evolution over time"
) {
    private val path by argument("path", help = "Repository path").default(".")
    private val months by option("--months", help = "Months back to analyze").int().default(6)
    private val interval by option("--interval", help = "Days between snapshots").int().default(30)

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        require(months > 0) { "--months must be greater than 0" }
        require(interval > 0) { "--interval must be greater than 0" }
        echo("⏳ Starting Temporal Analysis (Time Machine)...")
        echo("   Looking back $months months, every $interval days.")

        val analyzer = TemporalAnalyzer(root.path)
        try {
            val snapshots = analyzer.analyzeEvolution(months, interval)

            echo("\n📈 Evolution Report")
            echo("------------------------------------------------")
            echo("Timestamp            | Commit  | Files | Lines")
            echo("------------------------------------------------")
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

            snapshots.forEach { snapshot ->
                echo(
                    "${formatter.format(snapshot.timestamp)} | ${snapshot.commitHash.take(7)} | " +
                        "${snapshot.totalFiles.toString().padStart(5)} | ${snapshot.totalLines}"
                )
            }

            if (snapshots.isEmpty()) {
                throw IllegalStateException("No historical snapshots were found. Is this a git repository with commit history?")
            }

            val initialFiles = snapshots.first().totalFiles
            val finalFiles = snapshots.last().totalFiles
            val growth = if (initialFiles > 0) {
                ((finalFiles - initialFiles).toDouble() / initialFiles) * 100
            } else 0.0

            echo("\n📊 Net file growth: ${String.format("%.1f", growth)}% ($initialFiles → $finalFiles files)")
        } catch (e: Exception) {
            throw IllegalStateException("Evolution analysis failed: ${e.message ?: e::class.simpleName}", e)
        }
    }
}
