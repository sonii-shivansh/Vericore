package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.evidence.SemanticEvidenceGraphArtifact
import com.vericore.core.intelligence.ANALYSIS_SCHEMA_VERSION
import com.vericore.core.intelligence.AnalysisMetrics
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.ArchitectureSnapshot
import com.vericore.core.intelligence.RepositorySnapshot
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EvidenceGraphCommandTest {
    @Test
    fun rendersGraphSummaryThroughSharedHumanOutputContract() {
        val artifact = SemanticEvidenceGraphArtifact(
            schemaVersion = SemanticEvidenceGraphArtifact.SCHEMA_VERSION,
            repositoryId = "/workspace/repo",
            observedCommit = "abc123",
            digest = "digest-xyz",
            nodeCount = 0,
            edgeCount = 0,
            nodes = emptyList(),
            edges = emptyList()
        )

        assertEquals(
            """🔗 Semantic Evidence Graph

Summary
  Repository: /workspace/repo
  Commit: abc123
  Nodes: 0
  Edges: 0
  Graph digest: digest-xyz""",
            renderSemanticEvidenceGraphSummary(artifact)
        )
        assertEquals(
            """🔗 Semantic Evidence Graph

Report
  Semantic evidence graph: /repo/output/semantic-evidence-graph.json

Summary
  Repository: /workspace/repo
  Commit: abc123
  Nodes: 0
  Edges: 0
  Graph digest: digest-xyz""",
            renderSemanticEvidenceGraphSummary(
                artifact,
                "/repo/output/semantic-evidence-graph.json"
            )
        )
    }

    @Test
    fun defaultModeKeepsHumanSummaryAndDoesNotWriteGraphArtifact() {
        val root = createRepositoryWithAnalysisSnapshot()
        try {
            val result = EvidenceGraphCommand().test(root.absolutePath.replace("\\", "/"))

            assertEquals(0, result.statusCode)
            assertTrue(result.stdout.contains("Semantic Evidence Graph"))
            assertTrue(result.stdout.contains("Repository: ${root.absolutePath}"))
            assertTrue(result.stdout.contains("Graph digest:"))
            assertFalse(root.resolve("output/semantic-evidence-graph.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun jsonModeWritesCompatibleArtifactAndKeepsHumanSummary() {
        val root = createRepositoryWithAnalysisSnapshot()
        try {
            val repositoryArgument = root.absolutePath.replace("\\", "/")
            val result = EvidenceGraphCommand().test("$repositoryArgument --json")
            val artifact = root.resolve("output/semantic-evidence-graph.json")

            assertEquals(0, result.statusCode)
            assertTrue(artifact.isFile, "JSON mode should write output/semantic-evidence-graph.json")
            assertTrue(result.stdout.contains("Semantic Evidence Graph"))
            assertTrue(result.stdout.contains("Semantic evidence graph: ${artifact.path}"))
            assertTrue(result.stdout.contains("Graph digest:"))

            val json = Json.parseToJsonElement(artifact.readText()).jsonObject
            assertEquals(SemanticEvidenceGraphArtifact.SCHEMA_VERSION, json["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals(root.absolutePath, json["repositoryId"]?.jsonPrimitive?.content)
            assertEquals("abc123", json["observedCommit"]?.jsonPrimitive?.content)
            assertTrue(json["digest"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
            assertTrue(json.containsKey("nodes"))
            assertTrue(json.containsKey("edges"))
            assertTrue(json["nodeCount"]?.jsonPrimitive?.content?.toIntOrNull() != null)
            assertTrue(json["edgeCount"]?.jsonPrimitive?.content?.toIntOrNull() != null)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingAnalysisSnapshotStillFailsBeforeWritingGraphArtifact() {
        val root = Files.createTempDirectory("vericore-evidence-graph-missing-analysis").toFile()
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                EvidenceGraphCommand().test(root.absolutePath.replace("\\", "/"))
            }

            assertTrue(failure.message.orEmpty().contains("Analysis snapshot not found"))
            assertFalse(root.resolve("output/semantic-evidence-graph.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createRepositoryWithAnalysisSnapshot(): File {
        val root = Files.createTempDirectory("vericore-evidence-graph-contract").toFile()
        val snapshot = AnalysisSnapshot(
            schemaVersion = ANALYSIS_SCHEMA_VERSION,
            repository = RepositorySnapshot(
                path = root.absolutePath,
                analyzedAtEpochMillis = 1L,
                languages = listOf("Kotlin"),
                repositoryCommit = "abc123",
                repositoryStateDigest = "state-digest"
            ),
            metrics = AnalysisMetrics(
                totalFiles = 0,
                totalNodes = 0,
                totalEdges = 0,
                cycleDetected = false
            ),
            files = emptyList(),
            hotspots = emptyList(),
            architecture = ArchitectureSnapshot(
                hasCycles = false,
                packageCount = 0,
                crossPackageEdges = 0
            )
        )
        val outputDir = root.resolve("output")
        outputDir.mkdirs()
        outputDir.resolve("analysis-snapshot.json").writeText(
            Json { encodeDefaults = true }.encodeToString(snapshot)
        )
        return root
    }
}
