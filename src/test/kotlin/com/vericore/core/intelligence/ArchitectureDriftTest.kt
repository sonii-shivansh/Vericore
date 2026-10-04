package com.vericore.core.intelligence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ArchitectureDriftTest : FunSpec({
    fun result(
        findings: List<ArchitectureFinding> = emptyList(),
        cycles: List<ArchitectureCycle> = emptyList(),
        layers: Map<String, Int> = emptyMap(),
        dependencyEdges: Int = 2
    ) = ArchitectureIntelligenceResult(
        schemaVersion = "1.0",
        summary = ArchitectureSummary(3, dependencyEdges, findings.size, cycles.size, 1, 0),
        findings = findings,
        cycles = cycles,
        layers = layers
    )

    test("detects added and removed findings deterministically") {
        val removed = ArchitectureFinding("ARCH-A", "HIGH", "a.kt", "b.kt", "dependency", "old")
        val added = ArchitectureFinding("ARCH-B", "MEDIUM", "c.kt", relationship = "coupling", evidence = "new")

        val drift = ArchitectureDriftEngine.compare(
            result(findings = listOf(removed)),
            result(findings = listOf(added))
        )

        drift.summary.addedFindings shouldBe 1
        drift.summary.removedFindings shouldBe 1
        drift.changes.map { it.type } shouldContainExactly listOf("FINDING_ADDED", "FINDING_REMOVED")
        drift.changes.map { it.key } shouldContainExactly listOf("ARCH-B|c.kt||coupling|new", "ARCH-A|a.kt|b.kt|dependency|old")
    }

    test("detects new and removed cycles independent of member ordering") {
        val baseline = result(cycles = listOf(ArchitectureCycle(listOf("b.kt", "a.kt"))))
        val current = result(cycles = listOf(ArchitectureCycle(listOf("c.kt", "b.kt"))))

        val drift = ArchitectureDriftEngine.compare(baseline, current)

        drift.summary.newCycles shouldBe 1
        drift.summary.removedCycles shouldBe 1
        drift.changes.map { it.key } shouldContainExactly listOf("b.kt|c.kt", "a.kt|b.kt")
    }

    test("detects layer count changes") {
        val drift = ArchitectureDriftEngine.compare(
            result(layers = mapOf("api" to 2, "domain" to 3)),
            result(layers = mapOf("api" to 4, "domain" to 3, "repository" to 1))
        )

        drift.summary.changedLayers shouldBe 2
        drift.changes.map { it.key } shouldContainExactly listOf("api", "repository")
    }

    test("detects dependency edge count changes") {
        val drift = ArchitectureDriftEngine.compare(
            result(dependencyEdges = 3),
            result(dependencyEdges = 4)
        )

        drift.summary.dependencyEdgesDelta shouldBe 1
        drift.changes.map { it.type } shouldContainExactly listOf("DEPENDENCY_EDGE_COUNT_CHANGED")
        drift.changes.single().detail shouldBe "Dependency edge count changed from 3 to 4 (added 1)"
    }

    test("identical results produce no drift") {
        val snapshot = result(
            findings = listOf(ArchitectureFinding("ARCH-A", "HIGH", "a.kt", relationship = "coupling", evidence = "same")),
            cycles = listOf(ArchitectureCycle(listOf("a.kt", "b.kt"))),
            layers = mapOf("api" to 1)
        )

        val drift = ArchitectureDriftEngine.compare(snapshot, snapshot)

        drift.summary shouldBe ArchitectureDriftSummary(0, 0, 0, 0, 0, 0)
        drift.changes shouldBe emptyList()
    }
})
