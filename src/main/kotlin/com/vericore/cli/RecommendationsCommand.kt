package com.vericore.cli

import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.EngineeringRecommendation
import com.vericore.core.intelligence.RecommendationEngine
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

class RecommendationsCommand : CliktCommand(
    name = "recommendations",
    help = "Generate deterministic engineering recommendations from an analysis snapshot"
) {
    private val path by argument("path", help = "Repository path").default(".")
    private val jsonOutput by option("--json", help = "Write machine-readable recommendations JSON").flag()

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        require(snapshotFile.isFile) { "Analysis snapshot not found: ${snapshotFile.absolutePath}. Run `vericore analyze` or `vericore scan` first." }
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
        val recommendations = RecommendationEngine.generate(snapshot)
        if (jsonOutput) {
            val output = root.resolve("output/recommendations.json")
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(recommendations))
            echo("💡 Recommendations: ${output.absolutePath}")
        }
        echo("💡 Engineering Recommendations")
        recommendations.forEachIndexed { index, recommendation ->
            echo("${index + 1}. [${recommendation.priority}] ${recommendation.title}")
            echo("   ${recommendation.reason}")
            echo("   Action: ${recommendation.action}")
            if (recommendation.evidencePaths.isNotEmpty()) {
                echo("   Evidence: ${recommendation.evidencePaths.joinToString(", ")}")
            }
        }
    }
}
