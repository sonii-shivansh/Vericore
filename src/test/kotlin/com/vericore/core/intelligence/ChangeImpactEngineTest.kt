package com.vericore.core.intelligence

import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeImpactEngineTest {
    private fun graph(): DefaultDirectedGraph<String, DefaultEdge> {
        val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
        listOf(
            "/repo/a/Controller.kt",
            "/repo/a/Service.kt",
            "/repo/b/Repository.kt",
            "/repo/b/RepositoryTest.kt",
            "/repo/c/Facade.kt"
        ).forEach(graph::addVertex)
        graph.addEdge("/repo/a/Controller.kt", "/repo/a/Service.kt")
        graph.addEdge("/repo/b/Repository.kt", "/repo/a/Service.kt")
        graph.addEdge("/repo/c/Facade.kt", "/repo/b/Repository.kt")
        graph.addEdge("/repo/b/RepositoryTest.kt", "/repo/b/Repository.kt")
        return graph
    }

    @Test
    fun `impact walks reverse dependency graph`() {
        val result = ChangeImpactEngine.analyze(
            graph = graph(),
            changedPaths = listOf("/repo/a/Service.kt"),
            pageRankScores = mapOf("/repo/a/Service.kt" to 0.08),
            packageByPath = mapOf(
                "/repo/a/Service.kt" to "a",
                "/repo/a/Controller.kt" to "a",
                "/repo/b/Repository.kt" to "b",
                "/repo/b/RepositoryTest.kt" to "b",
                "/repo/c/Facade.kt" to "c"
            )
        )

        assertEquals(1, result.summary.changedFiles)
        assertEquals(5, result.nodes.size)
        assertTrue(result.nodes.any { it.path.endsWith("Controller.kt") && it.depth == 1 })
        assertTrue(result.nodes.any { it.path.endsWith("Repository.kt") && it.depth == 1 })
        assertTrue(result.nodes.any { it.path.endsWith("Facade.kt") && it.depth == 2 })
        assertTrue(result.nodes.any { it.path.endsWith("RepositoryTest.kt") && it.depth == 2 })
    }

    @Test
    fun `repository relative changed path resolves to graph vertex`() {
        val result = ChangeImpactEngine.analyze(graph(), listOf("a/Service.kt"))

        assertEquals(1, result.summary.changedFiles)
        assertEquals(listOf("/repo/a/Service.kt"), result.changedPaths)
        assertTrue(result.nodes.any { it.path.endsWith("Controller.kt") && it.depth == 1 })
    }

    @Test
    fun `test candidates are identified deterministically`() {
        val result = ChangeImpactEngine.analyze(graph(), listOf("/repo/b/Repository.kt"))
        assertEquals(1, result.summary.testCandidates)
        val candidate = result.nodes.single { it.relationship == ImpactRelationship.TEST_CANDIDATE }
        assertTrue(candidate.path.endsWith("RepositoryTest.kt"))
    }

    @Test
    fun `test candidate unreachable in dependency graph is still included in nodes and testCandidates`() {
        val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
        graph.addVertex("/repo/Service.kt")
        graph.addVertex("/repo/Controller.kt")
        graph.addVertex("/repo/ServiceTest.kt")
        graph.addEdge("/repo/Controller.kt", "/repo/Service.kt")

        val result = ChangeImpactEngine.analyze(graph, listOf("/repo/Service.kt"))

        assertEquals(1, result.summary.testCandidates)
        assertEquals(listOf("/repo/ServiceTest.kt"), result.testCandidates)
        val candidate = result.nodes.find { it.relationship == ImpactRelationship.TEST_CANDIDATE }
        assertTrue(candidate != null, "Unreachable test candidate must be present in nodes")
        assertEquals("/repo/ServiceTest.kt", candidate.path)
        assertEquals(-1, candidate.depth)
        assertTrue(candidate.reasons.any { it.contains("candidate test suite") })
    }

    @Test
    fun `unknown changed files are ignored without corrupting analysis`() {
        val result = ChangeImpactEngine.analyze(graph(), listOf("/repo/missing.kt", "/repo/a/Service.kt"))
        assertEquals(listOf("/repo/a/Service.kt"), result.changedPaths)
        assertEquals(5, result.nodes.size)
    }

    @Test
    fun `windows separators resolve to the same graph vertices`() {
        val windowsGraph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
        windowsGraph.addVertex("C:\\repo\\Service.kt")
        windowsGraph.addVertex("C:\\repo\\Controller.kt")
        windowsGraph.addEdge("C:\\repo\\Controller.kt", "C:\\repo\\Service.kt")

        val result = ChangeImpactEngine.analyze(windowsGraph, listOf("C:/repo/Service.kt"))

        assertEquals(listOf("C:/repo/Service.kt"), result.changedPaths)
        assertTrue(result.nodes.any { it.path.endsWith("Controller.kt") && it.depth == 1 })
    }

    @Test
    fun `results are deterministic`() {
        val first = ChangeImpactEngine.analyze(graph(), listOf("/repo/a/Service.kt"))
        val second = ChangeImpactEngine.analyze(graph(), listOf("/repo/a/Service.kt"))
        assertEquals(first, second)
    }
}
