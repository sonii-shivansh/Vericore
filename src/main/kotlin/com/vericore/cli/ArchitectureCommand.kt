package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class ArchitectureCommand : CliktCommand(name = "architecture") {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Analyze repository architecture and detect structural violations"
    private val path by argument("path", help = "Repository path")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable architecture JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save architecture JSON artifact")

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

        val defaultOutput = outputPath(root)
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(result))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val status = if (result.summary.findings == 0 && result.summary.cycles == 0) CommandStatus.PASS else CommandStatus.WARNING

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "architecture",
                status = status,
                summary = "Analyzed ${result.summary.filesAnalyzed} files: ${result.summary.findings} findings, ${result.summary.cycles} cycles, ${result.summary.crossLayerDependencies} cross-layer dependencies",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = result,
                findings = result.findings.map { finding ->
                    EnvelopeFinding(
                        level = finding.severity,
                        message = "${finding.ruleId}: ${finding.evidence}",
                        ruleId = finding.ruleId,
                        path = finding.source
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("architecture-report", defaultOutput.path)
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
            dest.writeText(JsonEnvelope.json.encodeToString(result))
        }

        echo(renderArchitectureSummary(result, outputPath = defaultOutput.absolutePath, includeExplanation = true))
    }
}

internal fun renderArchitectureSummary(
    result: ArchitectureIntelligenceResult,
    outputPath: String? = null,
    includeExplanation: Boolean = false
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
                        val targetStr = finding.target?.let { " -> $it" } ?: ""
                        val evidenceStr = if (includeExplanation && finding.evidence.isNotBlank()) " (${finding.evidence})" else ""
                        "${finding.severity} ${finding.ruleId}: ${finding.source}$targetStr$evidenceStr"
                    }.toTypedArray()
                )
            )
        }
    }
    return HumanCommandOutput.render(
        title = "🏛️ Architecture Intelligence",
        sections = sections
    )
}

private fun outputPath(root: File): File = root.resolve("output/architecture.json")
