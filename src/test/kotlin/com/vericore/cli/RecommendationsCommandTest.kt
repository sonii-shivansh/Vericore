package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.intelligence.ANALYSIS_SCHEMA_VERSION
import com.vericore.core.intelligence.AnalysisMetrics
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.ArchitectureSnapshot
import com.vericore.core.intelligence.EngineeringRecommendation
import com.vericore.core.intelligence.FileSnapshot
import com.vericore.core.intelligence.RecommendationPriority
import com.vericore.core.intelligence.RepositorySnapshot
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RecommendationsCommandTest {
    @Test
    fun rendersRecommendationsThroughSharedHumanOutputContract() {
        assertEquals(
            """💡 Engineering Recommendations

1. [HIGH] Add focused tests
  Review the risky path before changing it.
  Action: Add regression coverage.
  Evidence: src/main.kt, src/test/mainTest.kt""",
            renderRecommendationsSummary(
                listOf(
                    EngineeringRecommendation(
                        id = "TEST_COVERAGE",
                        priority = RecommendationPriority.HIGH,
                        title = "Add focused tests",
                        reason = "Review the risky path before changing it.",
                        evidencePaths = listOf("src/main.kt", "src/test/mainTest.kt"),
                        action = "Add regression coverage."
                    )
                )
            )
        )
    }

    @Test
    fun jsonModeKeepsArtifactPathAndHumanSummary() {
        val root = Files.createTempDirectory("vericore-recommendations-contract").toFile()
        try {
            val snapshot = AnalysisSnapshot(
                schemaVersion = ANALYSIS_SCHEMA_VERSION,
                repository = RepositorySnapshot(root.absolutePath, 1L, listOf("Kotlin")),
                metrics = AnalysisMetrics(1, 1, 0, false),
                files = listOf(FileSnapshot(root.resolve("A.kt").absolutePath, "example", 0, 0, emptyList(), 0.0, 0, 0)),
                hotspots = emptyList(),
                architecture = ArchitectureSnapshot(false, 1, 0)
            )
            val snapshotFile = root.resolve("output/analysis-snapshot.json")
            snapshotFile.parentFile.mkdirs()
            snapshotFile.writeText(Json { encodeDefaults = true }.encodeToString(snapshot))

            val result = RecommendationsCommand().test("${root.absolutePath} --json")
            val artifact = root.resolve("output/recommendations.json")

            assertEquals(0, result.statusCode)
            assertTrue(artifact.isFile, "JSON artifact should retain output/recommendations.json")
            assertTrue(result.stdout.contains("Engineering Recommendations"))
            assertTrue(result.stdout.contains("Recommendations: ${artifact.absolutePath}"))
            val json = Json.parseToJsonElement(artifact.readText()).jsonArray
            assertEquals("BASELINE_HEALTHY", json.first().jsonObject["id"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }
}
