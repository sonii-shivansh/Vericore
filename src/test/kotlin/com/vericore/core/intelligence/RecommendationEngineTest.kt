package com.vericore.core.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationEngineTest {
    @Test
    fun recommendsHighPriorityActionsFromDeterministicSignals() {
        val snapshot = AnalysisSnapshot(
            schemaVersion = ANALYSIS_SCHEMA_VERSION,
            repository = RepositorySnapshot(
                path = "/repo",
                analyzedAtEpochMillis = 1L,
                languages = listOf("Kotlin")
            ),
            metrics = AnalysisMetrics(
                totalFiles = 1,
                totalNodes = 1,
                totalEdges = 1,
                cycleDetected = true,
                parseFailures = 2
            ),
            files = listOf(
                FileSnapshot(
                    path = "/repo/Hot.kt",
                    packageName = "com.example",
                    importCount = 40,
                    churn = 25,
                    authors = listOf("dev"),
                    pageRank = 0.08,
                    dependents = 12,
                    dependencies = 1
                )
            ),
            hotspots = listOf(HotspotSnapshot("/repo/Hot.kt", 0.08, 25, 12, 1)),
            architecture = ArchitectureSnapshot(
                hasCycles = true,
                packageCount = 1,
                crossPackageEdges = 2
            )
        )

        val recommendations = RecommendationEngine.generate(snapshot)
        assertEquals(4, recommendations.size)
        assertEquals(RecommendationPriority.CRITICAL, recommendations[0].priority)
        assertEquals("RISK_CRITICAL_HOTSPOTS", recommendations[0].id)
        assertTrue(recommendations.any { it.id == "ARCHITECTURE_CYCLES" })
        assertTrue(recommendations.any { it.id == "PARSER_FAILURES" })
        assertTrue(recommendations.any { it.id == "CROSS_PACKAGE_COUPLING" })
    }

    @Test
    fun emitsHealthyBaselineWhenNoPrioritySignalExists() {
        val snapshot = AnalysisSnapshot(
            schemaVersion = ANALYSIS_SCHEMA_VERSION,
            repository = RepositorySnapshot("/repo", 1L, listOf("Kotlin")),
            metrics = AnalysisMetrics(1, 1, 0, false, 0),
            files = listOf(FileSnapshot("/repo/A.kt", "a", 1, 0, emptyList(), 0.0, 0, 0)),
            hotspots = emptyList(),
            architecture = ArchitectureSnapshot(false, 1, 0)
        )
        val recommendations = RecommendationEngine.generate(snapshot)
        assertEquals(1, recommendations.size)
        assertEquals("BASELINE_HEALTHY", recommendations.single().id)
        assertEquals(RecommendationPriority.LOW, recommendations.single().priority)
    }
}
