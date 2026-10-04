package com.vericore.core.ai

import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.FileSnapshot
import java.nio.file.Path
import kotlinx.serialization.Serializable

/** A repository fact that can be cited by the AI reasoning layer. */
@Serializable
data class EvidenceCitation(
    val id: String,
    val type: String,
    val path: String? = null,
    val detail: String,
    val metrics: Map<String, String> = emptyMap(),
    val relatedPaths: List<String> = emptyList()
)

@Serializable
data class GroundedEvidence(
    val schemaVersion: String = "1.0",
    val citations: List<EvidenceCitation>
) {
    init {
        require(citations.map { it.id }.distinct().size == citations.size) {
            "Evidence citation IDs must be unique"
        }
    }
}

/**
 * Converts deterministic analysis output into compact evidence for AI prompts.
 * The model never becomes the source of these facts.
 */
data class DependencyPaths(
    val dependents: List<String> = emptyList(),
    val dependencies: List<String> = emptyList()
)

object GroundedEvidenceBuilder {
    fun fromSnapshot(
        snapshot: AnalysisSnapshot,
        maxCitations: Int = 24,
        dependencyPaths: Map<String, DependencyPaths> = emptyMap()
    ): GroundedEvidence {
        require(maxCitations > 0) { "maxCitations must be positive" }

        val citations = buildList {
            add(
                EvidenceCitation(
                    id = "repo.metrics",
                    type = "repository-metrics",
                    detail = "Repository contains ${snapshot.metrics.totalFiles} analyzed files, ${snapshot.metrics.totalNodes} graph nodes and ${snapshot.metrics.totalEdges} dependency edges.",
                    metrics = mapOf(
                        "files" to snapshot.metrics.totalFiles.toString(),
                        "nodes" to snapshot.metrics.totalNodes.toString(),
                        "edges" to snapshot.metrics.totalEdges.toString(),
                        "parseFailures" to snapshot.metrics.parseFailures.toString()
                    )
                )
            )

            snapshot.hotspots.take(10).forEachIndexed { index, hotspot ->
                add(
                    EvidenceCitation(
                        id = "hotspot.${index + 1}",
                        type = "hotspot",
                        path = repositoryRelativePath(snapshot.repository.path, hotspot.path),
                        detail = graphDetail(
                            "Dependency-centrality hotspot identified by the deterministic analysis.",
                            dependencyPaths[hotspot.path]?.dependents.orEmpty()
                        ),
                        metrics = mapOf(
                            "score" to hotspot.score.toString(),
                            "dependents" to hotspot.dependents.toString(),
                            "dependencies" to hotspot.dependencies.toString(),
                            "churn" to hotspot.churn.toString()
                        ),
                        relatedPaths = dependencyPaths[hotspot.path]?.dependents.orEmpty().sorted()
                    )
                )
            }

            snapshot.files
                .sortedWith(compareByDescending<FileSnapshot> { it.dependents }.thenBy { it.path })
                .take(10)
                .forEachIndexed { index, file ->
                    add(
                        EvidenceCitation(
                            id = "file.${index + 1}",
                            type = "file-graph-fact",
                            path = repositoryRelativePath(snapshot.repository.path, file.path),
                            detail = graphDetail(
                                "File participates in the analyzed dependency graph.",
                                dependencyPaths[file.path]?.dependents.orEmpty()
                            ),
                            metrics = mapOf(
                                "dependents" to file.dependents.toString(),
                                "dependencies" to file.dependencies.toString(),
                                "churn" to file.churn.toString(),
                                "pageRank" to file.pageRank.toString()
                            ),
                            relatedPaths = dependencyPaths[file.path]?.dependents.orEmpty().sorted()
                        )
                    )
                }

            add(
                EvidenceCitation(
                    id = "architecture.summary",
                    type = "architecture-summary",
                    detail = "Architecture snapshot reports ${snapshot.architecture.packageCount} packages, ${snapshot.architecture.crossPackageEdges} cross-package edges and cycles=${snapshot.architecture.hasCycles}.",
                    metrics = mapOf(
                        "packages" to snapshot.architecture.packageCount.toString(),
                        "crossPackageEdges" to snapshot.architecture.crossPackageEdges.toString(),
                        "cycles" to snapshot.architecture.hasCycles.toString()
                    )
                )
            )
        }

        return GroundedEvidence(citations = citations.take(maxCitations))
    }

    private fun graphDetail(base: String, dependents: List<String>): String =
        if (dependents.isEmpty()) base else "$base Direct dependents: ${dependents.sorted().joinToString(", ")}."

    private fun repositoryRelativePath(repository: String, file: String): String {
        val root = Path.of(repository).toAbsolutePath().normalize()
        val candidate = Path.of(file).toAbsolutePath().normalize()
        return if (candidate.startsWith(root)) {
            root.relativize(candidate).toString().replace('\\', '/')
        } else {
            "<outside-repository>"
        }
    }
}
