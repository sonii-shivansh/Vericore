package com.vericore.cli

import com.vericore.core.reality.EngineeringRealitySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealityCommandTest {
    @Test
    fun rendersRealitySummaryThroughSharedHumanOutputContract() {
        val reality = EngineeringRealitySnapshot(
            schemaVersion = "1.0",
            repositoryCommit = "abc123",
            analysisSchemaVersion = "2.0",
            contextSchemaVersion = "1.0",
            languages = listOf("Kotlin"),
            fileCount = 12,
            graphNodes = 30,
            graphEdges = 42,
            parseFailures = 2,
            hasCycles = true,
            packageCount = 4,
            crossPackageEdges = 3,
            hotspotCount = 1,
            dirty = false,
            changedPaths = emptyList(),
            analysisDigest = "analysis-digest",
            contextDigest = "context-digest",
            realityDigest = "reality-digest"
        )

        assertEquals(
            """🧭 Engineering Reality

Repository
  Commit: abc123
  Files: 12
  Graph: 30 nodes / 42 edges
  Parse failures: 2
  Cycles: detected
  Dirty: false
  Reality digest: reality-digest""",
            renderEngineeringRealitySummary(reality)
        )
        val outputPath = "/repo/output/engineering-reality.json"
        val summary = renderEngineeringRealitySummary(reality, outputPath)
        assertEquals(
            """🧭 Engineering Reality

Report
  Engineering reality: /repo/output/engineering-reality.json

Repository
  Commit: abc123
  Files: 12
  Graph: 30 nodes / 42 edges
  Parse failures: 2
  Cycles: detected
  Dirty: false
  Reality digest: reality-digest""",
            summary
        )
        assertEquals(1, Regex(Regex.escape(outputPath)).findAll(summary).count())
    }

    @Test
    fun rendersUnknownCommitWhenSnapshotHasNoRepositoryCommit() {
        val reality = EngineeringRealitySnapshot(
            schemaVersion = "1.0",
            repositoryCommit = null,
            analysisSchemaVersion = "2.0",
            contextSchemaVersion = "1.0",
            languages = emptyList(),
            fileCount = 0,
            graphNodes = 0,
            graphEdges = 0,
            parseFailures = 0,
            hasCycles = false,
            packageCount = 0,
            crossPackageEdges = 0,
            hotspotCount = 0,
            dirty = false,
            changedPaths = emptyList(),
            analysisDigest = "analysis",
            contextDigest = "context",
            realityDigest = "digest"
        )

        assertTrue(renderEngineeringRealitySummary(reality).contains("Commit: unknown"))
    }
}
