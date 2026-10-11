package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureDriftEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArchitectureDriftCommand : CliktCommand(
    name = "architecture-drift",
    help = "Compare current architecture intelligence with a baseline snapshot"
) {
    private val path by argument("path", help = "Repository path")
    private val baseline by option("--baseline", help = "Baseline architecture JSON file").required()
    private val jsonOutput by option("--json", help = "Write machine-readable drift JSON").flag()

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

        if (jsonOutput) {
            val output = outputPath(root)
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(drift))
        }

        echo(renderArchitectureDriftSummary(drift, if (jsonOutput) outputPath(root).canonicalPath else null))
    }
}

internal fun renderArchitectureDriftSummary(
    result: com.vericore.core.intelligence.ArchitectureDriftResult,
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
