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
            val output = root.resolve("output/architecture-drift.json")
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(drift))
            echo("🏛️ Architecture drift report: ${output.canonicalPath}")
        }

        echo("🏛️ Architecture Drift")
        echo("├─ Added findings: ${drift.summary.addedFindings}")
        echo("├─ Removed findings: ${drift.summary.removedFindings}")
        echo("├─ New cycles: ${drift.summary.newCycles}")
        echo("├─ Removed cycles: ${drift.summary.removedCycles}")
        echo("├─ Changed layers: ${drift.summary.changedLayers}")
        echo("├─ Added edges: ${drift.summary.addedEdges}")
        echo("└─ Removed edges: ${drift.summary.removedEdges}")
        drift.changes.take(20).forEach { change -> echo("   ${change.type}: ${change.key}") }
    }
}
