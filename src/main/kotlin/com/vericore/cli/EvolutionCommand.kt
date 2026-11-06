package com.vericore.cli

import com.vericore.core.temporal.CodebaseSnapshot
import com.vericore.core.temporal.TemporalAnalyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EvolutionCommand : CliktCommand(
    name = "evolution"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Analyze codebase evolution over time"
    private val path by argument("path", help = "Repository path").default(".")
    private val months by option("--months", help = "Months back to analyze").int().default(6)
    private val interval by option("--interval", help = "Days between snapshots").int().default(30)
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable evolution JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save evolution JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val isJson = outFormat == OutputFormat.JSON

        if (!isJson) {
            echo("⏳ Starting Temporal Analysis (Time Machine)...")
            echo("   Looking back $months months, every $interval days.")
        }

        val analyzer = TemporalAnalyzer(root.path)
        try {
            val snapshots = analyzer.analyzeEvolution(months, interval)
            if (snapshots.isEmpty()) {
                throw IllegalStateException("No historical snapshots were found. Is this a git repository with commit history?")
            }

            if (isJson) {
                val envelope = CommandEnvelope(
                    schemaVersion = "1.0",
                    command = "evolution",
                    status = CommandStatus.PASS,
                    summary = "Evolution analysis over $months months: ${snapshots.size} historical snapshots",
                    repository = RepositoryInfo(name = root.name, path = root.path),
                    data = snapshots,
                    execution = ExecutionInfo(exitCode = 0)
                )
                val jsonText = JsonEnvelope.encode(envelope)
                if (output != null) {
                    val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                    dest.parentFile?.mkdirs()
                    dest.writeText(jsonText)
                }
                echo(jsonText)
                return
            }

            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(JsonEnvelope.json.encodeToString(snapshots))
            }

            echo(renderEvolutionReport(snapshots))
        } catch (e: Exception) {
            throw IllegalStateException("Evolution analysis failed: ${e.message ?: e::class.simpleName}", e)
        }
    }
}

internal fun renderEvolutionReport(
    snapshots: List<CodebaseSnapshot>,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zoneId)
    val sections = buildList {
        add(
            HumanCommandOutput.section(
                "Snapshots",
                "Timestamp            | Commit  | Files | Lines",
                *snapshots.map { snapshot ->
                    "${formatter.format(snapshot.timestamp)} | ${snapshot.commitHash.take(7)} | " +
                        "${snapshot.totalFiles.toString().padStart(5)} | ${snapshot.totalLines}"
                }.toTypedArray()
            )
        )
        if (snapshots.isNotEmpty()) {
            val initialFiles = snapshots.first().totalFiles
            val finalFiles = snapshots.last().totalFiles
            val growth = if (initialFiles > 0) {
                ((finalFiles - initialFiles).toDouble() / initialFiles) * 100
            } else 0.0
            add(HumanCommandOutput.section("Growth", "Net file growth: ${String.format("%.1f", growth)}% ($initialFiles → $finalFiles files)"))
        }
    }
    return HumanCommandOutput.render(title = "📈 Evolution Report", sections = sections)
}
