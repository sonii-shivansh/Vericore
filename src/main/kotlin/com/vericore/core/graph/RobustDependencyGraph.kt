package com.vericore.core.graph

import com.vericore.core.parser.ParsedFile
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.alg.scoring.PageRank
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

class RobustDependencyGraph {
    val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
    val pageRankScores = mutableMapOf<String, Double>()
    var hasCycles = false

    fun build(parsedFiles: List<ParsedFile>): Result<Unit> {
        return try {
            graph.removeAllVertices(graph.vertexSet().toList())
            pageRankScores.clear()
            hasCycles = false
            val classMap = mutableMapOf<String, String>()
            val packageIndex = mutableMapOf<String, MutableList<String>>()

            parsedFiles.forEach { parsed ->
                val filePath = parsed.file.absolutePath
                graph.addVertex(filePath)
                val className = parsed.file.nameWithoutExtension
                val fqcn = if (parsed.packageName.isNotEmpty()) "${parsed.packageName}.$className" else className
                classMap[fqcn] = filePath
                packageIndex.getOrPut(parsed.packageName) { mutableListOf() }.add(filePath)
            }

            parsedFiles.forEach { source ->
                source.imports.forEach { imported ->
                    if (imported.endsWith(".*")) {
                        packageIndex[imported.removeSuffix(".*")].orEmpty().forEach { target ->
                            addEdgeSafely(source.file.absolutePath, target)
                        }
                    } else {
                        classMap[imported]?.let { target -> addEdgeSafely(source.file.absolutePath, target) }
                    }
                }

                source.referencedTypes.forEach { referencedType ->
                    val normalizedType = referencedType.removePrefix(".")
                    val samePackageFqcn = if (source.packageName.isNotEmpty() && !normalizedType.contains('.')) {
                        "${source.packageName}.$normalizedType"
                    } else {
                        normalizedType
                    }
                    classMap[samePackageFqcn]?.let { target ->
                        addEdgeSafely(source.file.absolutePath, target)
                    }
                }
            }

            if (graph.vertexSet().isNotEmpty()) {
                hasCycles = CycleDetector(graph).detectCycles()
                if (hasCycles) {
                    println("⚠️ Warning: Circular dependencies detected")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addEdgeSafely(source: String, target: String) {
        if (source != target && graph.containsVertex(source) && graph.containsVertex(target) && !graph.containsEdge(source, target)) {
            runCatching { graph.addEdge(source, target) }
        }
    }

    fun analyze(): Result<Unit> {
        return try {
            pageRankScores.clear()
            if (graph.vertexSet().isEmpty()) return Result.success(Unit)
            val pageRank = PageRank(graph, 0.85, 100)
            graph.vertexSet().forEach { vertex -> pageRankScores[vertex] = pageRank.getVertexScore(vertex) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTopHotspots(limit: Int = 10): List<Pair<String, Double>> =
        pageRankScores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
}
