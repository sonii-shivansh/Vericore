package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.reality.EngineeringRealityEngine
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

/** Builds one deterministic identity across the analysis and repository-state artifacts. */
class RealityCommand : CliktCommand(
    name = "reality",
    help = "Build a deterministic engineering-reality snapshot from the current repository state"
) {
    private val path by argument("path", help = "Repository path").default(".")
    private val jsonOutput by option("--json", help = "Write the machine-readable reality snapshot").flag()

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

        val output = if (jsonOutput) {
            outputDir.resolve("engineering-reality.json").also {
                it.writeText(EngineeringRealityEngine.encode(reality))
            }
        } else {
            null
        }

        echo(
            renderEngineeringRealitySummary(
                reality,
                output?.path
            )
        )
    }
}


internal fun renderEngineeringRealitySummary(
    reality: com.vericore.core.reality.EngineeringRealitySnapshot,
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
