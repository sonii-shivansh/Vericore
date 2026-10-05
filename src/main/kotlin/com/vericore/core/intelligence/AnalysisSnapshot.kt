package com.vericore.core.intelligence

import com.vericore.core.parser.ParsedFile
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import kotlinx.serialization.Serializable

const val ANALYSIS_SCHEMA_VERSION = "1.1"

/**
 * Stable, machine-readable representation of an analysis run.
 *
 * The snapshot is intentionally independent of HTML rendering and AI providers.
 * It is the contract future CI, SARIF, dashboards and grounded AI features can
 * consume without re-running presentation-specific logic.
 */
@Serializable
data class AnalysisSnapshot(
    val schemaVersion: String,
    val repository: RepositorySnapshot,
    val metrics: AnalysisMetrics,
    val files: List<FileSnapshot>,
    val hotspots: List<HotspotSnapshot>,
    val architecture: ArchitectureSnapshot
)

@Serializable
data class RepositorySnapshot(
    val path: String,
    val analyzedAtEpochMillis: Long,
    val languages: List<String>,
    /** Git HEAD observed while the analysis snapshot was produced, when available. */
    val repositoryCommit: String? = null,
    /** Digest of the source-file state observed while the analysis snapshot was produced. */
    val repositoryStateDigest: String? = null
)

@Serializable
data class AnalysisMetrics(
    val totalFiles: Int,
    val totalNodes: Int,
    val totalEdges: Int,
    val cycleDetected: Boolean,
    val parseFailures: Int = 0
)

@Serializable
data class FileSnapshot(
    val path: String,
    val packageName: String,
    val importCount: Int,
    val churn: Int,
    val authors: List<String>,
    val pageRank: Double,
    val dependents: Int,
    val dependencies: Int,
    val description: String = ""
)

@Serializable
data class HotspotSnapshot(
    val path: String,
    val score: Double,
    val churn: Int,
    val dependents: Int,
    val dependencies: Int
)

@Serializable
data class ArchitectureSnapshot(
    val hasCycles: Boolean,
    val packageCount: Int,
    val crossPackageEdges: Int
)

/** Builds a stable snapshot from the existing analysis primitives. */
object AnalysisSnapshotBuilder {
    fun build(
        repositoryPath: String,
        parsedFiles: List<ParsedFile>,
        graph: org.jgrapht.graph.DefaultDirectedGraph<String, org.jgrapht.graph.DefaultEdge>,
        pageRankScores: Map<String, Double>,
        hasCycles: Boolean,
        parseFailures: Int = 0,
        repositoryState: EngineeringContextSnapshot? = null
    ): AnalysisSnapshot {
        val packageNames = parsedFiles.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        val fileByPath = parsedFiles.associateBy { it.file.absolutePath }
        val orderedFiles = parsedFiles.sortedBy { it.file.absolutePath }

        // Bind the analysis to the exact repository state it observed. Callers that
        // already computed the engineering context can supply it to avoid rescanning
        // and re-hashing every repository file a second time.
        val observedRepositoryState = repositoryState ?: runCatching {
            EngineeringContextEngine.snapshot(File(repositoryPath).canonicalFile, RepositoryScanner())
        }.getOrNull()

        val files = orderedFiles.map { file ->
            val path = file.file.absolutePath
            FileSnapshot(
                path = path,
                packageName = file.packageName,
                importCount = file.imports.size,
                churn = file.gitMetadata.changeFrequency,
                authors = file.gitMetadata.topAuthors,
                pageRank = pageRankScores[path] ?: 0.0,
                dependents = if (graph.containsVertex(path)) graph.inDegreeOf(path) else 0,
                dependencies = if (graph.containsVertex(path)) graph.outDegreeOf(path) else 0,
                description = file.description
            )
        }

        val hotspots = pageRankScores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(20)
            .map { (path, score) ->
                HotspotSnapshot(
                    path = path,
                    score = score,
                    churn = fileByPath[path]?.gitMetadata?.changeFrequency ?: 0,
                    dependents = if (graph.containsVertex(path)) graph.inDegreeOf(path) else 0,
                    dependencies = if (graph.containsVertex(path)) graph.outDegreeOf(path) else 0
                )
            }

        var crossPackageEdges = 0
        graph.edgeSet().forEach { edge ->
            val source = graph.getEdgeSource(edge)
            val target = graph.getEdgeTarget(edge)
            val sourcePackage = fileByPath[source]?.packageName
            val targetPackage = fileByPath[target]?.packageName
            if (!sourcePackage.isNullOrBlank() && !targetPackage.isNullOrBlank() && sourcePackage != targetPackage) {
                crossPackageEdges++
            }
        }

        val languages = orderedFiles.mapNotNull { file ->
            when (file.file.extension.lowercase()) {
                "kt", "kts" -> "Kotlin"
                "java" -> "Java"
                else -> null
            }
        }.distinct().sorted()

        return AnalysisSnapshot(
            schemaVersion = ANALYSIS_SCHEMA_VERSION,
            repository = RepositorySnapshot(
                path = repositoryPath,
                analyzedAtEpochMillis = System.currentTimeMillis(),
                languages = languages,
                repositoryCommit = observedRepositoryState?.repositoryCommit,
                repositoryStateDigest = observedRepositoryState?.snapshotDigest
            ),
            metrics = AnalysisMetrics(
                totalFiles = orderedFiles.size,
                totalNodes = graph.vertexSet().size,
                totalEdges = graph.edgeSet().size,
                cycleDetected = hasCycles,
                parseFailures = parseFailures
            ),
            files = files,
            hotspots = hotspots,
            architecture = ArchitectureSnapshot(
                hasCycles = hasCycles,
                packageCount = packageNames.size,
                crossPackageEdges = crossPackageEdges
            )
        )
    }
}
