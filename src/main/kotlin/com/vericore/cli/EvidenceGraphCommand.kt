package com.vericore.cli

import com.vericore.core.evidence.SemanticEvidenceGraphArtifact
import com.vericore.core.evidence.SemanticEvidenceGraphArtifactCodec
import com.vericore.core.evidence.SemanticEvidenceGraphBuilder
import com.vericore.core.intelligence.AnalysisSnapshot
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

/** Emits the deterministic semantic evidence graph used by Reality and grounding. */
class EvidenceGraphCommand : CliktCommand(
    name = "evidence-graph"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Build a deterministic semantic evidence graph from the current analysis snapshot"
    private val path by argument("path", help = "Repository path").default(".")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable graph JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save graph JSON artifact")

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

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val defaultOutput = outputDir.resolve("semantic-evidence-graph.json")
        if (outFormat == OutputFormat.JSON) {
            defaultOutput.writeText(SemanticEvidenceGraphArtifactCodec.encode(artifact))
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "evidence-graph",
                status = CommandStatus.PASS,
                summary = "Semantic evidence graph built: ${artifact.nodeCount} nodes, ${artifact.edgeCount} edges, digest ${artifact.digest.take(12)}",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = artifact,
                artifacts = listOf(
                    EnvelopeArtifact("semantic-evidence-graph", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        if (output != null) {
            val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
            dest.parentFile?.mkdirs()
            dest.writeText(SemanticEvidenceGraphArtifactCodec.encode(artifact))
        }

        echo(renderSemanticEvidenceGraphSummary(artifact, output))
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
