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
    name = "inspect",
    help = "Inspect repository architecture, risk, hotspots, and next actions"
) {
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val jsonOutput by option("--json", help = "Write machine-readable inspection JSON").flag()

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

        if (jsonOutput) {
            val output = root.resolve("output/inspection.json")
            output.parentFile.mkdirs()
            val json = Json { prettyPrint = true; encodeDefaults = true }
            val details = json.encodeToString(inspection)
            output.writeText(details)
            echo(productJson(ProductCommandResult(
                command = "inspect",
                status = "COMPLETED",
                repository = root.path,
                findings = recommendations.take(5).map { ProductFinding(it.priority.toString(), "${it.title}: ${it.action}") },
                artifacts = listOf(ProductArtifact("inspection", output.path)),
                nextStep = "vericore repo-qa \"What should I know before changing this repository?\" --path ${root.path}",
                details = json.parseToJsonElement(details)
            )))
            return
        }

        echo("")
        echo("🔎 Vericore Inspection")
        echo("Repository  ${inspection.repository}")
        echo("Languages   ${inspection.languages.joinToString(", ").ifBlank { "Unknown" }}")
        echo("Files       ${inspection.metrics.files}")
        echo("Graph       ${inspection.metrics.nodes} nodes / ${inspection.metrics.edges} edges")
        echo("Architecture ${if (inspection.metrics.cyclesDetected) "cycles detected" else "no cycles detected"}")
        echo("Risk        ${inspection.highRiskFiles} high/critical · ${inspection.criticalRiskFiles} critical")
        echo("")

        echo("Top hotspots")
        if (inspection.hotspots.isEmpty()) {
            echo("  none")
        } else {
            inspection.hotspots.forEachIndexed { index, hotspot ->
                echo("  ${index + 1}. ${hotspot.path} (${String.format("%.4f", hotspot.score)})")
            }
        }

        echo("")
        echo("Next actions")
        inspection.recommendations.take(3).forEachIndexed { index, recommendation ->
            echo("  ${index + 1}. [${recommendation.priority}] ${recommendation.title}")
            echo("     ${recommendation.action}")
        }

        echo("")
        echo("Next step")
        echo("  Ask a grounded question: vericore repo-qa \"What should I know before changing this repository?\" --path $path")
    }
}

@Serializable
private data class InspectionOutput(
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
private data class InspectionMetrics(
    val files: Int,
    val nodes: Int,
    val edges: Int,
    val parseFailures: Int,
    val cyclesDetected: Boolean,
    val packages: Int,
    val crossPackageEdges: Int
)
