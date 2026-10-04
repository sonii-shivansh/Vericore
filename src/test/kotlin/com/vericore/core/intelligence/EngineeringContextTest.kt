package com.vericore.core.intelligence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EngineeringContextTest : StringSpec({
    "diff detects added removed and modified files deterministically" {
        val before = EngineeringContextSnapshot(
            ENGINEERING_CONTEXT_SCHEMA_VERSION, "a", listOf(
                ContextFile("a.java", "111", 1),
                ContextFile("b.java", "222", 2),
                ContextFile("c.java", "333", 3)
            ), 3, 6, listOf("Java"), false, emptyList(), "before"
        )
        val after = EngineeringContextSnapshot(
            ENGINEERING_CONTEXT_SCHEMA_VERSION, "b", listOf(
                ContextFile("a.java", "999", 1),
                ContextFile("b.java", "222", 2),
                ContextFile("d.java", "444", 4)
            ), 3, 7, listOf("Java"), true, listOf("a.java"), "after"
        )

        val diff = EngineeringContextEngine.diff(before, after)

        diff.summary.added shouldBe 1
        diff.summary.removed shouldBe 1
        diff.summary.modified shouldBe 1
        diff.summary.unchanged shouldBe 1
        diff.changes.map { it.path } shouldBe listOf("a.java", "c.java", "d.java")
        diff.changes.map { it.type } shouldBe listOf("MODIFIED", "REMOVED", "ADDED")
    }

    "encoded snapshot round-trips into context-diff input" {
        val snapshot = EngineeringContextSnapshot(
            ENGINEERING_CONTEXT_SCHEMA_VERSION, "abc", listOf(
                ContextFile("src/App.kt", "111", 10)
            ), 1, 10, listOf("Kotlin"), false, emptyList(), "digest"
        )
        val encoded = EngineeringContextEngine.encode(snapshot)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
        val decoded = json.decodeFromString(EngineeringContextSnapshot.serializer(), encoded)

        decoded shouldBe snapshot
        val diff = EngineeringContextEngine.diff(snapshot, decoded)
        diff.summary shouldBe ContextDiffSummary(0, 0, 0, 1)
    }

    "identical snapshots produce no changes" {
        val snapshot = EngineeringContextSnapshot(
            ENGINEERING_CONTEXT_SCHEMA_VERSION, "a", listOf(ContextFile("a.java", "111", 1)),
            1, 1, listOf("Java"), false, emptyList(), "digest"
        )
        val diff = EngineeringContextEngine.diff(snapshot, snapshot)
        diff.summary shouldBe ContextDiffSummary(0, 0, 0, 1)
        diff.changes shouldBe emptyList()
    }

    "snapshot excludes Vericore and legacy generated output" {
        val root = java.nio.file.Files.createTempDirectory("vericore-context-output-").toFile()
        try {
            root.resolve("src/App.kt").apply {
                parentFile.mkdirs()
                writeText("class App")
            }
            root.resolve("output/verify.json").apply {
                parentFile.mkdirs()
                writeText("generated")
            }
            root.resolve("output/generated.kt").writeText("class Generated")
            root.resolve(".vericore/cache.kt").apply {
                parentFile.mkdirs()
                writeText("class Cache")
            }
            root.resolve(".vericore-architecture-contract.json").writeText("generated contract")
            root.resolve(".codecontext/cache.kt").apply {
                parentFile.mkdirs()
                writeText("class LegacyCache")
            }
            root.resolve(".codecontext-architecture-contract.json").writeText("legacy generated contract")

            val snapshot = EngineeringContextEngine.snapshot(
                root,
                com.vericore.core.scanner.RepositoryScanner(
                    com.vericore.core.config.VericoreConfig(excludePaths = emptyList())
                )
            )

            snapshot.files.map { it.path } shouldBe listOf("src/App.kt")
            snapshot.changedPaths shouldBe emptyList()
            snapshot.languages shouldBe listOf("Kotlin")
            snapshot.dirty shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }
})
