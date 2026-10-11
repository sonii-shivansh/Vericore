package com.vericore.cli

import com.vericore.core.evidence.SemanticEvidenceGraphArtifact
import com.vericore.core.evidence.SemanticEvidenceGraphArtifactCodec
import com.vericore.core.evidence.SemanticEvidenceGraphBuilder
import com.vericore.core.intelligence.AnalysisSnapshot
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

/** Emits the deterministic semantic evidence graph used by Reality and grounding. */
class EvidenceGraphCommand : CliktCommand(
    name = "evidence-graph",
    help = "Build a deterministic semantic evidence graph from the current analysis snapshot"
) {
    private val path by argument("path", help = "Repository path").default(".")
    private val jsonOutput by option("--json", help = "Write output/semantic-evidence-graph.json").flag()

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
        val graph = SemanticEvidenceGraphBuilder.build(analysis)
        val artifact = SemanticEvidenceGraphArtifact.fromGraph(graph)

        val artifactPath = if (jsonOutput) {
            val output = outputDir.resolve("semantic-evidence-graph.json")
            output.writeText(SemanticEvidenceGraphArtifactCodec.encode(artifact))
            output.path
        } else {
            null
        }

        echo(renderSemanticEvidenceGraphSummary(artifact, artifactPath))
    }
}

internal fun renderSemanticEvidenceGraphSummary(
    artifact: SemanticEvidenceGraphArtifact,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Semantic evidence graph: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Repository: ${artifact.repositoryId}",
                "Commit: ${artifact.observedCommit}",
                "Nodes: ${artifact.nodeCount}",
                "Edges: ${artifact.edgeCount}",
                "Graph digest: ${artifact.digest}"
            )
        )
    }
    return HumanCommandOutput.render(title = "🔗 Semantic Evidence Graph", sections = sections)
}
