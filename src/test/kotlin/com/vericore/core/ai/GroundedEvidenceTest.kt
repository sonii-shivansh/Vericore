package com.vericore.core.ai

import com.vericore.core.intelligence.AnalysisMetrics
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.ArchitectureSnapshot
import com.vericore.core.intelligence.FileSnapshot
import com.vericore.core.intelligence.HotspotSnapshot
import com.vericore.core.intelligence.RepositorySnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe

class GroundedEvidenceTest : FunSpec({
    test("builds deterministic evidence with stable citation ids") {
        val snapshot = snapshot()

        val first = GroundedEvidenceBuilder.fromSnapshot(snapshot)
        val second = GroundedEvidenceBuilder.fromSnapshot(snapshot)

        first shouldBe second
        first.citations.map { it.id }.distinct().size shouldBe first.citations.size
        first.citations.map { it.id } shouldContain "repo.metrics"
        first.citations.map { it.id } shouldContain "architecture.summary"
    }

    test("limits evidence without changing citation ordering") {
        val evidence = GroundedEvidenceBuilder.fromSnapshot(snapshot(), maxCitations = 3)

        evidence.citations.size shouldBe 3
        evidence.citations.map { it.id } shouldBe listOf("repo.metrics", "hotspot.1", "hotspot.2")
    }

    test("exposes direct dependent paths for dependency questions") {
        val evidence = GroundedEvidenceBuilder.fromSnapshot(
            snapshot(),
            dependencyPaths = mapOf(
                "/repo/A.kt" to DependencyPaths(dependents = listOf("UsesA.kt", "OtherUsesA.kt"))
            )
        )
        val hotspot = evidence.citations.first { it.id == "hotspot.1" }

        hotspot.relatedPaths shouldBe listOf("OtherUsesA.kt", "UsesA.kt")
        hotspot.detail shouldContain "OtherUsesA.kt"
        hotspot.detail shouldContain "UsesA.kt"
    }

    test("exposes repository-relative paths instead of absolute filesystem paths") {
        val evidence = GroundedEvidenceBuilder.fromSnapshot(snapshot())
        val paths = evidence.citations.mapNotNull { it.path }

        paths shouldContain "A.kt"
        paths shouldContain "B.kt"
        paths.none { it.startsWith("/") || it.contains(":\\") } shouldBe true
    }
})

private fun snapshot() = AnalysisSnapshot(
    schemaVersion = "1.0",
    repository = RepositorySnapshot("/repo", 1L, listOf("Kotlin")),
    metrics = AnalysisMetrics(3, 3, 2, false, 0),
    files = listOf(
        FileSnapshot("/repo/A.kt", "a", 1, 2, listOf("dev"), 0.8, 3, 1),
        FileSnapshot("/repo/B.kt", "b", 2, 1, listOf("dev"), 0.2, 1, 1),
        FileSnapshot("/repo/C.kt", "c", 1, 0, emptyList(), 0.1, 0, 0)
    ),
    hotspots = listOf(
        HotspotSnapshot("/repo/A.kt", 0.8, 2, 3, 1),
        HotspotSnapshot("/repo/B.kt", 0.2, 1, 1, 1)
    ),
    architecture = ArchitectureSnapshot(false, 3, 1)
)
