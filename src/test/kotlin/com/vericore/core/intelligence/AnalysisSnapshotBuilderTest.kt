package com.vericore.core.intelligence

import com.vericore.core.parser.ParsedFile
import java.nio.file.Files
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisSnapshotBuilderTest {
    @Test
    fun `uses supplied repository state instead of rescanning the repository`() {
        val root = Files.createTempDirectory("vericore-snapshot-test").toFile()
        try {
            val source = root.resolve("One.java")
            source.writeText("class One {}")
            val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
            val parsed = listOf(ParsedFile(source, "", emptyList()))
            val supplied = EngineeringContextSnapshot(
                schemaVersion = ENGINEERING_CONTEXT_SCHEMA_VERSION,
                repositoryCommit = "provided-commit",
                files = emptyList(),
                totalFiles = 0,
                totalBytes = 0,
                languages = emptyList(),
                dirty = false,
                changedPaths = emptyList(),
                snapshotDigest = "provided-digest"
            )

            val snapshot = AnalysisSnapshotBuilder.build(
                repositoryPath = root.path,
                parsedFiles = parsed,
                graph = graph,
                pageRankScores = emptyMap(),
                hasCycles = false,
                repositoryState = supplied
            )

            assertEquals("provided-commit", snapshot.repository.repositoryCommit)
            assertEquals("provided-digest", snapshot.repository.repositoryStateDigest)
        } finally {
            root.deleteRecursively()
        }
    }
}
