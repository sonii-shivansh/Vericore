package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArchitectureCommand : CliktCommand(name = "architecture", help = "Analyze repository architecture and detect structural violations") {
    private val path by argument("path", help = "Repository path")
    private val jsonOutput by option("--json", help = "Write machine-readable architecture JSON").flag()

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
        val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(files) }
        val graph = RobustDependencyGraph()
        graph.build(parsed).getOrThrow()
        graph.analyze().getOrThrow()
        val result = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)
        if (jsonOutput) {
            val output = outputPath(root)
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(result))
        }
        echo(renderArchitectureSummary(result, outputPath = if (jsonOutput) outputPath(root).absolutePath else null))
    }
}

internal fun renderArchitectureSummary(
    result: com.vericore.core.intelligence.ArchitectureIntelligenceResult,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Architecture report: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Files analyzed: ${result.summary.filesAnalyzed}",
                "Dependency edges: ${result.summary.dependencyEdges}",
                "Findings: ${result.summary.findings}",
                "Cycles: ${result.summary.cycles}",
                "Cross-layer dependencies: ${result.summary.crossLayerDependencies}",
                "High-coupling files: ${result.summary.highCouplingFiles}"
            )
        )
        result.findings.take(20).takeIf { it.isNotEmpty() }?.let { findings ->
            add(
                HumanCommandOutput.section(
                    "Top findings",
                    *findings.map { finding ->
                        "${finding.severity} ${finding.ruleId}: ${finding.source}${finding.target?.let { " -> $it" } ?: ""}"
                    }.toTypedArray()
                )
            )
        }
    }
    return HumanCommandOutput.render(title = "🏛️ Architecture Intelligence", sections = sections)
}

private fun outputPath(root: File): File = root.resolve("output/architecture.json")
