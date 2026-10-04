package com.vericore.core.intelligence

import java.io.File
import kotlinx.serialization.Serializable
import org.jgrapht.Graph
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector
import org.jgrapht.graph.DefaultEdge

@Serializable
data class ArchitectureLayer(val name: String, val pathPatterns: List<String>, val allowedDependencies: List<String> = emptyList())

@Serializable
data class ArchitectureRuleConfig(
    val layers: List<ArchitectureLayer> = defaultArchitectureLayers(),
    val forbiddenDependencies: List<String> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
data class ArchitectureFinding(
    val ruleId: String,
    val severity: String,
    val source: String,
    val target: String? = null,
    val relationship: String,
    val evidence: String,
    val category: String = "architecture"
)

@Serializable
data class ArchitectureCycle(val members: List<String>)

@Serializable
data class ArchitectureDependencyEdge(val source: String, val target: String)

@Serializable
data class ArchitectureSummary(
    val filesAnalyzed: Int,
    val dependencyEdges: Int,
    val findings: Int,
    val cycles: Int,
    val crossLayerDependencies: Int,
    val highCouplingFiles: Int
)

@Serializable
data class ArchitectureIntelligenceResult(
    val schemaVersion: String,
    val summary: ArchitectureSummary,
    val findings: List<ArchitectureFinding>,
    val cycles: List<ArchitectureCycle>,
    val layers: Map<String, Int>,
    val dependencyEdges: List<ArchitectureDependencyEdge> = emptyList()
)

object ArchitectureIntelligenceEngine {
    fun analyze(graph: Graph<String, DefaultEdge>, repoRoot: File, ruleConfig: ArchitectureRuleConfig = ArchitectureRuleConfig()): ArchitectureIntelligenceResult {
        val dependencyEdges = graph.edgeSet()
            .map { edge ->
                ArchitectureDependencyEdge(
                    source = relativePath(graph.getEdgeSource(edge), repoRoot),
                    target = relativePath(graph.getEdgeTarget(edge), repoRoot)
                )
            }
            .distinct()
            .sortedWith(compareBy<ArchitectureDependencyEdge> { it.source }.thenBy { it.target })

        if (!ruleConfig.enabled) {
            return ArchitectureIntelligenceResult(
                "1.1",
                ArchitectureSummary(graph.vertexSet().size, graph.edgeSet().size, 0, 0, 0, 0),
                emptyList(),
                emptyList(),
                emptyMap(),
                dependencyEdges
            )
        }
        val layerByPath = graph.vertexSet().associateWith { path -> detectLayer(path, repoRoot, ruleConfig.layers) }
        val findings = mutableListOf<ArchitectureFinding>()
        var crossLayerDependencies = 0
        graph.edgeSet().forEach { edge ->
            val source = graph.getEdgeSource(edge)
            val target = graph.getEdgeTarget(edge)
            val sourceLayer = layerByPath[source]
            val targetLayer = layerByPath[target]
            if (sourceLayer != null && targetLayer != null && sourceLayer != targetLayer) {
                crossLayerDependencies++
                val allowed = ruleConfig.layers.firstOrNull { it.name == sourceLayer }?.allowedDependencies.orEmpty()
                if (targetLayer !in allowed) {
                    findings += ArchitectureFinding("ARCH-LAYER-001", "HIGH", relativePath(source, repoRoot), relativePath(target, repoRoot), "dependency", "$sourceLayer layer depends on $targetLayer layer")
                }
            }
            val explicitKey = "${sourceLayer ?: "*"}->${targetLayer ?: "*"}"
            if (explicitKey in ruleConfig.forbiddenDependencies) {
                findings += ArchitectureFinding("ARCH-BOUNDARY-001", "HIGH", relativePath(source, repoRoot), relativePath(target, repoRoot), "dependency", "Forbidden architecture boundary: $explicitKey")
            }
        }
        val cycles = KosarajuStrongConnectivityInspector(graph).stronglyConnectedSets()
            .filter { it.size > 1 }
            .map { ArchitectureCycle(it.map { path -> relativePath(path, repoRoot) }.sorted()) }
            .sortedBy { it.members.firstOrNull().orEmpty() }
        cycles.forEach { cycle -> findings += ArchitectureFinding("ARCH-CYCLE-001", "HIGH", cycle.members.first(), relationship = "cycle", evidence = "Strongly connected dependency component: ${cycle.members.joinToString(" -> ")}") }
        val highCouplingFiles = graph.vertexSet().filter { graph.inDegreeOf(it) + graph.outDegreeOf(it) >= 10 }.sorted()
        highCouplingFiles.forEach { vertex -> findings += ArchitectureFinding("ARCH-COUPLING-001", "MEDIUM", relativePath(vertex, repoRoot), relationship = "coupling", evidence = "High structural coupling: ${graph.inDegreeOf(vertex) + graph.outDegreeOf(vertex)} dependency relationships") }
        val layerCounts = layerByPath.values.filterNotNull().groupingBy { it }.eachCount().toSortedMap()
        val sortedFindings = findings.distinctBy { listOf(it.ruleId, it.source, it.target, it.relationship, it.evidence) }
            .sortedWith(compareBy<ArchitectureFinding> { it.severityOrder() }.thenBy { it.ruleId }.thenBy { it.source }.thenBy { it.target.orEmpty() })
        return ArchitectureIntelligenceResult("1.1", ArchitectureSummary(graph.vertexSet().size, graph.edgeSet().size, sortedFindings.size, cycles.size, crossLayerDependencies, highCouplingFiles.size), sortedFindings, cycles, layerCounts, dependencyEdges)
    }

    private fun detectLayer(path: String, repoRoot: File, layers: List<ArchitectureLayer>): String? {
        val normalized = relativePath(path, repoRoot).lowercase()
        return layers.firstOrNull { layer -> layer.pathPatterns.any { pattern -> matches(normalized, pattern) } }?.name
    }

    private fun matches(path: String, pattern: String): Boolean {
        val p = pattern.replace('\\', '/').trim('/').lowercase()
        val value = path.replace('\\', '/').lowercase()
        if (p.startsWith("**/")) return value.contains(p.removePrefix("**/"))
        if (p.endsWith("/**")) return value.startsWith(p.removeSuffix("/**").trimEnd('/') + "/")
        return value.contains(p)
    }

    private fun ArchitectureFinding.severityOrder(): Int = when (severity) { "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; "LOW" -> 3; else -> 4 }

    private fun relativePath(path: String, repoRoot: File): String = runCatching {
        repoRoot.toPath().toAbsolutePath().normalize().relativize(File(path).toPath().toAbsolutePath().normalize()).toString().replace('\\', '/')
    }.getOrDefault(File(path).name)
}

fun defaultArchitectureLayers(): List<ArchitectureLayer> = listOf(
    ArchitectureLayer("api", listOf("/controller/", "/controllers/", "/api/"), listOf("application", "service", "domain")),
    ArchitectureLayer("application", listOf("/service/", "/services/", "/usecase/", "/usecases/"), listOf("domain", "infrastructure", "repository")),
    ArchitectureLayer("domain", listOf("/domain/", "/model/", "/models/"), listOf("domain")),
    ArchitectureLayer("repository", listOf("/repository/", "/repositories/", "/dao/"), listOf("domain", "infrastructure")),
    ArchitectureLayer("infrastructure", listOf("/infrastructure/", "/client/", "/clients/", "/config/"), listOf("domain", "infrastructure"))
)
