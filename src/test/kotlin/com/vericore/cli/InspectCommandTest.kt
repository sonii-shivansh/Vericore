package com.vericore.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class InspectCommandTest {
    @Test
    fun rendersInspectionThroughSharedHumanOutputContract() {
        assertEquals(
            """🔎 Vericore Inspection

Repository
  /workspace/project
  Languages: Kotlin
  Files: 4
  Graph: 7 nodes / 5 edges
  Architecture: no cycles detected
  Risk: 1 high/critical · 0 critical

Top hotspots
  none

Next actions

Next step
  Ask a grounded question: vericore repo-qa "What should I know before changing this repository?" --path .""",
            renderInspectionSummary(
                InspectionOutput(
                    schemaVersion = "1.0",
                    repository = "/workspace/project",
                    analyzedAtEpochMillis = 1L,
                    languages = listOf("Kotlin"),
                    metrics = InspectionMetrics(
                        files = 4,
                        nodes = 7,
                        edges = 5,
                        parseFailures = 0,
                        cyclesDetected = false,
                        packages = 2,
                        crossPackageEdges = 1
                    ),
                    hotspots = emptyList(),
                    highRiskFiles = 1,
                    criticalRiskFiles = 0,
                    recommendations = emptyList()
                ),
                "."
            )
        )
    }
}
