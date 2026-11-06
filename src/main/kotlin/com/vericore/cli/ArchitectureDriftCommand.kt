package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureDriftEngine
import com.vericore.core.intelligence.ArchitectureDriftResult
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArchitectureDriftCommand : CliktCommand(
    name = "architecture-drift"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Compare current architecture intelligence with a baseline snapshot"
    private val path by argument("path", help = "Repository path")
    private val baseline by option("--baseline", help = "Baseline architecture JSON file").required()
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable drift JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save drift JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val baselineFile = File(baseline).canonicalFile
        require(baselineFile.isFile) { "Architecture baseline does not exist: $baseline" }

        val json = Json { ignoreUnknownKeys = false }
        val baselineResult = runCatching {
            json.decodeFromString<com.vericore.core.intelligence.ArchitectureIntelligenceResult>(baselineFile.readText())
        }.getOrElse { error("Invalid architecture baseline: ${it.message}") }

        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
        val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(files) }
        val graph = RobustDependencyGraph()
        graph.build(parsed).getOrThrow()
        graph.analyze().getOrThrow()
        val currentResult = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)
        val drift = ArchitectureDriftEngine.compare(baselineResult, currentResult)

        val defaultOutput = outputPath(root)
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(drift))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val hasChanges = drift.changes.isNotEmpty()
        val status = if (hasChanges) CommandStatus.WARNING else CommandStatus.PASS

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "architecture-drift",
                status = status,
                summary = "Architecture drift: ${drift.summary.addedFindings} added findings, ${drift.summary.newCycles} new cycles, ${drift.summary.addedEdges} added edges, ${drift.summary.removedEdges} removed edges",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = drift,
                findings = drift.changes.map { change ->
                    EnvelopeFinding(
                        level = if (change.type.contains("ADDED")) "WARNING" else "INFO",
                        message = "${change.type}: ${change.key}",
                        ruleId = change.type,
                        path = change.key
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("architecture-drift", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        if (output != null) {
            val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
            dest.parentFile?.mkdirs()
            dest.writeText(JsonEnvelope.json.encodeToString(drift))
        }

        echo(renderArchitectureDriftSummary(drift, defaultOutput.canonicalPath))
    }
}

internal fun renderArchitectureDriftSummary(
    result: ArchitectureDriftResult,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Architecture drift report: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Added findings: ${result.summary.addedFindings}",
                "Removed findings: ${result.summary.removedFindings}",
                "New cycles: ${result.summary.newCycles}",
                "Removed cycles: ${result.summary.removedCycles}",
                "Changed layers: ${result.summary.changedLayers}",
                "Added edges: ${result.summary.addedEdges}",
                "Removed edges: ${result.summary.removedEdges}"
            )
        )
        result.changes.take(20).takeIf { it.isNotEmpty() }?.let { changes ->
            add(HumanCommandOutput.section("Changes", *changes.map { "${it.type}: ${it.key}" }.toTypedArray()))
        }
    }
    return HumanCommandOutput.render(title = "🏛️ Architecture Drift", sections = sections)
}

private fun outputPath(root: File): File = root.resolve("output/architecture-drift.json")
