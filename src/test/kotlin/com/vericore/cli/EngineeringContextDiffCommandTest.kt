package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.intelligence.ContextDiffChange
import com.vericore.core.intelligence.ContextDiffSummary
import com.vericore.core.intelligence.EngineeringContextDiff
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineeringContextDiffCommandTest {
    @Test
    fun rendersSummaryAndChangesThroughSharedOutputContract() {
        val diff = EngineeringContextDiff(
            schemaVersion = "1.0",
            beforeCommit = "before123",
            afterCommit = "after456",
            summary = ContextDiffSummary(2, 1, 3, 8),
            changes = listOf(
                ContextDiffChange("ADDED", "src/New.kt"),
                ContextDiffChange("MODIFIED", "src/Main.kt")
            )
        )

        assertEquals(
            """🧭 Engineering Context Diff

Summary
  Added: 2
  Removed: 1
  Modified: 3
  Unchanged: 8

Changes
  ADDED: src/New.kt
  MODIFIED: src/Main.kt""",
            renderEngineeringContextDiffSummary(diff)
        )
    }

    @Test
    fun includesArtifactPathAndLimitsDisplayedChangesToThirty() {
        val changes = (1..32).map { ContextDiffChange("MODIFIED", "src/File$it.kt") }
        val diff = EngineeringContextDiff(
            schemaVersion = "1.0",
            beforeCommit = null,
            afterCommit = null,
            summary = ContextDiffSummary(0, 0, 32, 0),
            changes = changes
        )

        val rendered = renderEngineeringContextDiffSummary(diff, "output/engineering-context-diff.json")
        assertEquals(true, rendered.contains("Report\n  Context diff: output/engineering-context-diff.json"))
        assertEquals(true, rendered.contains("MODIFIED: src/File30.kt"))
        assertEquals(false, rendered.contains("MODIFIED: src/File31.kt"))
        assertEquals(false, rendered.contains("MODIFIED: src/File32.kt"))
    }

    @Test
    fun decodesBothRawSnapshotsAndWrappedCommandEnvelopes() {
        val tempDir = java.nio.file.Files.createTempDirectory("diff-unboxing-test").toFile()
        try {
            val snapshotA = com.vericore.core.intelligence.EngineeringContextSnapshot(
                schemaVersion = "1.0",
                repositoryCommit = "c1",
                files = listOf(com.vericore.core.intelligence.ContextFile("A.kt", "h1", 10L)),
                totalFiles = 1,
                totalBytes = 10L,
                languages = listOf("Kotlin"),
                dirty = false,
                changedPaths = emptyList(),
                snapshotDigest = "d1"
            )
            val snapshotB = com.vericore.core.intelligence.EngineeringContextSnapshot(
                schemaVersion = "1.0",
                repositoryCommit = "c2",
                files = listOf(com.vericore.core.intelligence.ContextFile("A.kt", "h1", 10L), com.vericore.core.intelligence.ContextFile("B.kt", "h2", 20L)),
                totalFiles = 2,
                totalBytes = 30L,
                languages = listOf("Kotlin"),
                dirty = false,
                changedPaths = emptyList(),
                snapshotDigest = "d2"
            )
            val rawFile = java.io.File(tempDir, "raw.json").apply {
                writeText(kotlinx.serialization.json.Json.encodeToString(com.vericore.core.intelligence.EngineeringContextSnapshot.serializer(), snapshotA))
            }
            val wrappedFile = java.io.File(tempDir, "wrapped.json").apply {
                val envelope = CommandEnvelope(
                    schemaVersion = "1.0",
                    command = "context-snapshot",
                    status = CommandStatus.PASS,
                    summary = "Snapshot summary",
                    data = snapshotB,
                    execution = ExecutionInfo(exitCode = 0)
                )
                writeText(JsonEnvelope.encode(envelope))
            }

            val cmd = EngineeringContextDiffCommand()
            val result = cmd.test(listOf(rawFile.absolutePath, wrappedFile.absolutePath, "--json"))
            assertEquals(0, result.statusCode)
            val envelope = kotlinx.serialization.json.Json.parseToJsonElement(result.stdout) as kotlinx.serialization.json.JsonObject
            assertEquals("context-diff", (envelope["command"] as kotlinx.serialization.json.JsonPrimitive).content)
            assertEquals("WARNING", (envelope["status"] as kotlinx.serialization.json.JsonPrimitive).content)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
