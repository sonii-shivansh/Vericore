package com.vericore.cli

import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.EngineeringRecommendation
import com.vericore.core.intelligence.RecommendationEngine
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

class RecommendationsCommand : CliktCommand(
    name = "recommendations"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Generate deterministic engineering recommendations from an analysis snapshot"
    private val path by argument("path", help = "Repository path").default(".")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable recommendations JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save recommendations JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        require(snapshotFile.isFile) { "Analysis snapshot not found: ${snapshotFile.absolutePath}. Run `vericore analyze` or `vericore scan` first." }
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
        val recommendations = RecommendationEngine.generate(snapshot)

        val defaultOutput = root.resolve("output/recommendations.json")
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(recommendations))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val isHealthy = recommendations.all { it.id == "BASELINE_HEALTHY" }
        val status = if (isHealthy) CommandStatus.NO_FINDINGS else CommandStatus.SUCCESS

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "recommendations",
                status = status,
                summary = "Generated ${recommendations.size} engineering recommendation(s)",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = recommendations,
                findings = recommendations.filter { it.id != "BASELINE_HEALTHY" }.map { rec ->
                    EnvelopeFinding(
                        level = rec.priority.name,
                        message = "${rec.title}: ${rec.reason}",
                        ruleId = rec.id,
                        path = rec.evidencePaths.firstOrNull()?.let { repositoryRelativePath(it, root) }
                    )
                },
                recommendations = recommendations.map { rec ->
                    EnvelopeRecommendation(
                        priority = rec.priority.name,
                        title = rec.title,
                        action = rec.action,
                        evidencePaths = rec.evidencePaths.map { repositoryRelativePath(it, root) }
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("recommendations", defaultOutput.path)
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
            dest.writeText(JsonEnvelope.json.encodeToString(recommendations))
        }

        echo(renderRecommendationsSummary(recommendations))
    }
}

/** Formats only the human summary; recommendation generation and JSON output remain unchanged. */
internal fun renderRecommendationsSummary(recommendations: List<EngineeringRecommendation>): String =
    HumanCommandOutput.render(
        title = "💡 Engineering Recommendations",
        sections = recommendations.mapIndexed { index, recommendation ->
            HumanCommandOutput.Section(
                title = "${index + 1}. [${recommendation.priority}] ${recommendation.title}",
                lines = buildList {
                    add(recommendation.reason)
                    add("Action: ${recommendation.action}")
                    if (recommendation.evidencePaths.isNotEmpty()) {
                        add("Evidence: ${recommendation.evidencePaths.joinToString(", ")}")
                    }
                }
            )
        }
    )
