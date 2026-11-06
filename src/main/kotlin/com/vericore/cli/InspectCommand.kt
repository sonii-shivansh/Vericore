package com.vericore.cli

import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.EngineeringRiskEngine
import com.vericore.core.intelligence.EngineeringRecommendation
import com.vericore.core.intelligence.RecommendationEngine
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Product-level inspection summary consuming the persisted deterministic snapshot. */
class InspectCommand : CliktCommand(
    name = "inspect"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Inspect repository architecture, risk, hotspots, and next actions"
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable inspection JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save inspection JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }

        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        require(snapshotFile.isFile) {
            "Analysis snapshot not found: ${snapshotFile.absolutePath}. Run `vericore scan --path $path` first."
        }

        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
        val risks = EngineeringRiskEngine.calculate(snapshot)
        val recommendations = RecommendationEngine.generate(snapshot)

        val inspection = InspectionOutput(
            schemaVersion = "1.0",
            repository = snapshot.repository.path,
            analyzedAtEpochMillis = snapshot.repository.analyzedAtEpochMillis,
            languages = snapshot.repository.languages,
            repositoryCommit = snapshot.repository.repositoryCommit,
            metrics = InspectionMetrics(
                files = snapshot.metrics.totalFiles,
                nodes = snapshot.metrics.totalNodes,
                edges = snapshot.metrics.totalEdges,
                parseFailures = snapshot.metrics.parseFailures,
                cyclesDetected = snapshot.metrics.cycleDetected,
                packages = snapshot.architecture.packageCount,
                crossPackageEdges = snapshot.architecture.crossPackageEdges
            ),
            hotspots = snapshot.hotspots.take(5),
            highRiskFiles = risks.count { it.level.name == "HIGH" || it.level.name == "CRITICAL" },
            criticalRiskFiles = risks.count { it.level.name == "CRITICAL" },
            recommendations = recommendations.take(5)
        )

        val defaultOutput = root.resolve("output/inspection.json")
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(inspection))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val status = if (inspection.criticalRiskFiles == 0) CommandStatus.PASS else CommandStatus.WARNING

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "inspect",
                status = status,
                summary = "Repository inspection: ${inspection.metrics.files} files, ${inspection.highRiskFiles} high-risk files, ${inspection.criticalRiskFiles} critical-risk files, ${inspection.recommendations.size} recommendations",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = inspection,
                findings = inspection.hotspots.map { h ->
                    EnvelopeFinding(
                        level = "INFO",
                        message = "Hotspot score ${String.format("%.4f", h.score)} (churn=${h.churn}, dependents=${h.dependents})",
                        path = repositoryRelativePath(h.path, root)
                    )
                },
                recommendations = inspection.recommendations.map { rec ->
                    EnvelopeRecommendation(
                        priority = rec.priority.name,
                        title = rec.title,
                        action = rec.action,
                        evidencePaths = rec.evidencePaths.map { repositoryRelativePath(it, root) }
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("inspection", defaultOutput.path)
                ),
                nextStep = "vericore recommendations ${root.path}",
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
            dest.writeText(JsonEnvelope.json.encodeToString(inspection))
        }

        echo(renderInspectionSummary(inspection, path))
    }
}

@Serializable
internal data class InspectionOutput(
    val schemaVersion: String,
    val repository: String,
    val analyzedAtEpochMillis: Long,
    val languages: List<String>,
    val repositoryCommit: String? = null,
    val metrics: InspectionMetrics,
    val hotspots: List<com.vericore.core.intelligence.HotspotSnapshot>,
    val highRiskFiles: Int,
    val criticalRiskFiles: Int,
    val recommendations: List<EngineeringRecommendation>
)

@Serializable
internal data class InspectionMetrics(
    val files: Int,
    val nodes: Int,
    val edges: Int,
    val parseFailures: Int,
    val cyclesDetected: Boolean,
    val packages: Int,
    val crossPackageEdges: Int
)

/** Formats only the human summary; inspection data and JSON serialization remain unchanged. */
internal fun renderInspectionSummary(inspection: InspectionOutput, path: String): String =
    HumanCommandOutput.render(
        title = "🔎 Vericore Inspection",
        sections = listOf(
            HumanCommandOutput.section(
                "Repository",
                inspection.repository,
                "Languages: ${inspection.languages.joinToString(", ").ifBlank { "Unknown" }}",
                "Files: ${inspection.metrics.files}",
                "Graph: ${inspection.metrics.nodes} nodes / ${inspection.metrics.edges} edges",
                "Architecture: ${if (inspection.metrics.cyclesDetected) "cycles detected" else "no cycles detected"}",
                "Risk: ${inspection.highRiskFiles} high/critical · ${inspection.criticalRiskFiles} critical"
            ),
            HumanCommandOutput.Section(
                title = "Top hotspots",
                lines = if (inspection.hotspots.isEmpty()) listOf("none") else
                    inspection.hotspots.mapIndexed { index, hotspot ->
                        "${index + 1}. ${hotspot.path} (${String.format("%.4f", hotspot.score)})"
                    }
            ),
            HumanCommandOutput.Section(
                title = "Next actions",
                lines = inspection.recommendations.take(3).flatMapIndexed { index, recommendation ->
                    listOf(
                        "${index + 1}. [${recommendation.priority}] ${recommendation.title}",
                        recommendation.action
                    )
                }
            )
        ),
        nextStep = "Ask a grounded question: vericore repo-qa \"What should I know before changing this repository?\" --path $path"
    )
