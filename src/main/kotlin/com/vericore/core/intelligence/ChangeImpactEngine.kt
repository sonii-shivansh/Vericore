package com.vericore.core.intelligence

import java.io.File
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

@Serializable
enum class ImpactRelationship { CHANGED, DIRECT_DEPENDENT, TRANSITIVE_DEPENDENT, TEST_CANDIDATE }

@Serializable
data class ImpactNode(
    val path: String,
    val relationship: ImpactRelationship,
    val depth: Int,
    val score: Double,
    val reasons: List<String>
)

@Serializable
data class ImpactSummary(
    val changedFiles: Int,
    val impactedFiles: Int,
    val impactedPackages: Int,
    val crossPackageImpacts: Int,
    val testCandidates: Int,
    val maxDepth: Int
)

@Serializable
data class ChangeImpactResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: String = "1.0",
    val changedPaths: List<String>,
    val nodes: List<ImpactNode>,
    val summary: ImpactSummary,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val testCandidates: List<String> = emptyList()
)

/** Deterministic reverse-dependency analysis suitable for CI and AI grounding. */
object ChangeImpactEngine {
    fun analyze(
        graph: DefaultDirectedGraph<String, DefaultEdge>,
        changedPaths: Collection<String>,
        pageRankScores: Map<String, Double> = emptyMap(),
        churnByPath: Map<String, Int> = emptyMap(),
        packageByPath: Map<String, String> = emptyMap()
    ): ChangeImpactResult {
        val vertexByNormalizedPath = graph.vertexSet().associateBy(::normalize)
        val pageRankByNormalizedPath = pageRankScores.mapKeys { normalize(it.key) }
        val churnByNormalizedPath = churnByPath.mapKeys { normalize(it.key) }
        val packageByNormalizedPath = packageByPath.mapKeys { normalize(it.key) }
        val normalizedChanges = changedPaths
            .map(::normalize)
            .mapNotNull { resolveGraphPath(it, vertexByNormalizedPath) }
            .distinct()
            .sorted()
        val changedVertices = normalizedChanges.map { vertexByNormalizedPath.getValue(it) }
        val distances = linkedMapOf<String, Int>()
        val queue = ArrayDeque<String>()

        changedVertices.forEach {
            distances[it] = 0
            queue.addLast(it)
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val depth = distances.getValue(current)
            graph.incomingEdgesOf(current).forEach { edge ->
                val dependent = graph.getEdgeSource(edge)
                if (dependent !in distances) {
                    distances[dependent] = depth + 1
                    queue.addLast(dependent)
                }
            }
        }

        val testPaths = findTestCandidates(graph.vertexSet(), normalizedChanges)
        val graphNodes = distances.entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { normalize(it.key) })
            .map { (path, depth) ->
                val normalizedPath = normalize(path)
                val changed = depth == 0
                val testCandidate = path in testPaths
                val relationship = when {
                    changed -> ImpactRelationship.CHANGED
                    testCandidate -> ImpactRelationship.TEST_CANDIDATE
                    depth == 1 -> ImpactRelationship.DIRECT_DEPENDENT
                    else -> ImpactRelationship.TRANSITIVE_DEPENDENT
                }
                val reasons = buildList {
                    if (changed) add("explicitly changed")
                    if (depth == 1) add("direct dependent of a changed file")
                    if (depth > 1) add("transitively depends on a changed file")
                    if ((pageRankByNormalizedPath[normalizedPath] ?: 0.0) >= 0.02) add("notable dependency centrality")
                    if ((churnByNormalizedPath[normalizedPath] ?: 0) >= 10) add("frequently changed file")
                    if (testCandidate) add("likely test coverage candidate")
                }.ifEmpty { listOf("reachable through dependency graph") }
                val score = score(depth, pageRankByNormalizedPath[normalizedPath] ?: 0.0, churnByNormalizedPath[normalizedPath] ?: 0)
                ImpactNode(path, relationship, depth, score, reasons)
            }

        // Include any identified test candidates that were not reached during reverse-dependency traversal
        val traversedPaths = distances.keys.toSet()
        val unreachableTestNodes = testPaths.filter { it !in traversedPaths }.sorted().map { testPath ->
            val normalizedPath = normalize(testPath)
            ImpactNode(
                path = testPath,
                relationship = ImpactRelationship.TEST_CANDIDATE,
                depth = -1,
                score = 50.0,
                reasons = listOf("test candidate matching changed file name (independent of dependency graph)", "candidate test suite", "likely test coverage candidate")
            )
        }
        val allNodes = graphNodes + unreachableTestNodes

        val impactedNonChanged = allNodes.filter { it.relationship != ImpactRelationship.CHANGED }
        val impactedPackages = impactedNonChanged.mapNotNull { packageByNormalizedPath[normalize(it.path)] }.filter { it.isNotBlank() }.toSet().size
        val changedPackages = normalizedChanges.mapNotNull { packageByNormalizedPath[it] }.filter { it.isNotBlank() }.toSet()
        val crossPackageImpacts = impactedNonChanged.count {
            val pkg = packageByNormalizedPath[normalize(it.path)]
            pkg != null && pkg.isNotBlank() && pkg !in changedPackages
        }

        return ChangeImpactResult(
            changedPaths = normalizedChanges,
            nodes = allNodes,
            summary = ImpactSummary(
                changedFiles = normalizedChanges.size,
                impactedFiles = impactedNonChanged.count { it.relationship != ImpactRelationship.TEST_CANDIDATE },
                impactedPackages = impactedPackages,
                crossPackageImpacts = crossPackageImpacts,
                testCandidates = testPaths.size,
                maxDepth = distances.values.maxOrNull() ?: 0
            ),
            testCandidates = testPaths.sorted()
        )
    }

    private fun resolveGraphPath(input: String, vertices: Map<String, String>): String? {
        vertices[input]?.let { return normalize(it) }
        if (input.isBlank() || input.contains(":/")) return null
        val suffix = "/${input.trimStart('/')}"
        val matches = vertices.keys.filter { it.endsWith(suffix) }
        return matches.singleOrNull()
    }

    private fun findTestCandidates(vertices: Set<String>, changedPaths: List<String>): Set<String> {
        val changedNames = changedPaths.map { File(it).nameWithoutExtension.removeSuffix("Test").removeSuffix("Tests") }.toSet()
        return vertices.filter { path ->
            val normalized = normalize(path)
            val name = File(normalized).nameWithoutExtension
            val looksLikeTest = name.endsWith("Test") || name.endsWith("Tests") || normalized.contains("/test/")
            looksLikeTest && changedNames.any { name.contains(it) }
        }.toSet()
    }

    private fun score(depth: Int, pageRank: Double, churn: Int): Double {
        val depthSignal = if (depth == 0) 100.0 else (70.0 / depth).coerceAtLeast(10.0)
        val centralitySignal = (pageRank * 300.0).coerceIn(0.0, 20.0)
        val churnSignal = (churn * 0.5).coerceIn(0.0, 10.0)
        return (depthSignal + centralitySignal + churnSignal).coerceIn(0.0, 100.0)
    }

    private fun normalize(path: String): String = path.replace('\\', '/')
}
