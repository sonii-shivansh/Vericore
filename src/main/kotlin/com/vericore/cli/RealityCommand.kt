package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.reality.EngineeringRealityEngine
import com.vericore.core.reality.EngineeringRealitySnapshot
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

/** Builds one deterministic identity across the analysis and repository-state artifacts. */
class RealityCommand : CliktCommand(
    name = "reality"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Build a deterministic engineering-reality snapshot from the current repository state"
    private val path by argument("path", help = "Repository path").default(".")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write the machine-readable reality snapshot to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save reality snapshot JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }

        val outputDir = root.resolve("output").apply { mkdirs() }
        val analysisFile = outputDir.resolve("analysis-snapshot.json")
        require(analysisFile.isFile) {
            "Analysis snapshot not found at ${analysisFile.path}. Run 'vericore analyze ${root.path}' first."
        }

        val json = Json { ignoreUnknownKeys = false }
        val analysis = json.decodeFromString(AnalysisSnapshot.serializer(), analysisFile.readText())
        val context = EngineeringContextEngine.snapshot(
            root,
            RepositoryScanner(ConfigLoader.loadForRepository(root.path))
        )
        val reality = EngineeringRealityEngine.build(analysis, context)

        val defaultOutput = outputDir.resolve("engineering-reality.json")
        defaultOutput.writeText(EngineeringRealityEngine.encode(reality))

        if (output != null) {
            val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
            dest.parentFile?.mkdirs()
            dest.writeText(EngineeringRealityEngine.encode(reality))
        }

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "reality",
                status = CommandStatus.PASS,
                summary = "Engineering reality snapshot: ${reality.fileCount} files, ${reality.graphNodes} nodes / ${reality.graphEdges} edges, cycles=${reality.hasCycles}, dirty=${reality.dirty}, digest=${reality.realityDigest.take(12)}",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = reality,
                artifacts = listOf(
                    EnvelopeArtifact("engineering-reality", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            echo(JsonEnvelope.encode(envelope))
            return
        }

        echo(renderEngineeringRealitySummary(reality, defaultOutput.path))
    }
}

internal fun renderEngineeringRealitySummary(
    reality: EngineeringRealitySnapshot,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Engineering reality: $it")) }
        add(
            HumanCommandOutput.section(
                "Repository",
                "Commit: ${reality.repositoryCommit ?: "unknown"}",
                "Files: ${reality.fileCount}",
                "Graph: ${reality.graphNodes} nodes / ${reality.graphEdges} edges",
                "Parse failures: ${reality.parseFailures}",
                "Cycles: ${if (reality.hasCycles) "detected" else "none detected"}",
                "Dirty: ${reality.dirty}",
                "Reality digest: ${reality.realityDigest}"
            )
        )
    }
    return HumanCommandOutput.render(title = "🧭 Engineering Reality", sections = sections)
}
