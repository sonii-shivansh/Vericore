package com.vericore.verification

import com.vericore.cli.CodeParallelParser
import com.vericore.core.config.VericoreConfig
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackendVerificationTest {

    @Test
    fun `verify backend scanner parser and graph on isolated fixture`() {
        val rootDir = kotlin.io.path.createTempDirectory("vericore-backend-fixture-").toFile()
        try {
            val sourceFile = File(rootDir, "Source.kt").apply {
                writeText(
                    """
                    package fixture

                    import fixture.Target

                    class Source {
                        private val target = Target()
                    }
                    """.trimIndent()
                )
            }
            val targetFile = File(rootDir, "Target.kt").apply {
                writeText(
                    """
                    package fixture

                    class Target
                    """.trimIndent()
                )
            }

            val scanner = RepositoryScanner(VericoreConfig(excludePaths = emptyList()))
            val files = scanner.scan(rootDir.absolutePath)
            assertEquals(setOf(sourceFile.name, targetFile.name), files.map { it.name }.toSet())

            val parser = CodeParallelParser()
            val parsedFiles = runBlocking { parser.parseFiles(files) }
            assertEquals(files.size, parsedFiles.size, "Should parse every fixture source file")

            val parsedSource = parsedFiles.single { it.file.name == sourceFile.name }
            assertTrue(parsedSource.imports.contains("fixture.Target"))

            val graphBuilder = RobustDependencyGraph()
            val buildResult = graphBuilder.build(parsedFiles)
            assertTrue(buildResult.isSuccess, "Graph build should succeed")

            val graph = graphBuilder.graph
            assertTrue(graph.vertexSet().isNotEmpty(), "Graph should contain fixture vertices")
            val sourceVertex = graph.vertexSet().single { it.replace('\\', '/').endsWith("/Source.kt") }
            val targetVertex = graph.vertexSet().single { it.replace('\\', '/').endsWith("/Target.kt") }
            assertTrue(
                graph.containsEdge(sourceVertex, targetVertex),
                "Graph should contain the Source -> Target dependency"
            )

            val analyzeResult = graphBuilder.analyze()
            assertTrue(analyzeResult.isSuccess, "Graph analysis should succeed")
            assertTrue(graphBuilder.getTopHotspots(5).isNotEmpty(), "Should calculate hotspots")
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
