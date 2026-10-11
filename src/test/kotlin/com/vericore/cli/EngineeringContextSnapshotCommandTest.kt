package com.vericore.cli

import com.vericore.core.intelligence.EngineeringContextSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineeringContextSnapshotCommandTest {
    @Test
    fun rendersSnapshotSummaryWithoutArtifactPathByDefault() {
        val snapshot = EngineeringContextSnapshot(
            schemaVersion = "1.0",
            repositoryCommit = "abc123",
            files = emptyList(),
            totalFiles = 12,
            totalBytes = 4096,
            languages = listOf("Kotlin"),
            dirty = true,
            changedPaths = listOf("src/Main.kt"),
            snapshotDigest = "digest-xyz"
        )

        assertEquals(
            """🧭 Engineering Context

Summary
  Commit: abc123
  Files: 12
  Bytes: 4096
  Dirty: true
  Digest: digest-xyz""",
            renderEngineeringContextSnapshotSummary(snapshot)
        )
    }

    @Test
    fun includesArtifactPathWhenJsonOutputIsRequested() {
        val snapshot = EngineeringContextSnapshot(
            schemaVersion = "1.0",
            repositoryCommit = null,
            files = emptyList(),
            totalFiles = 0,
            totalBytes = 0,
            languages = emptyList(),
            dirty = false,
            changedPaths = emptyList(),
            snapshotDigest = "empty-digest"
        )

        assertEquals(
            """🧭 Engineering Context

Report
  Engineering context: /repo/output/engineering-context.json

Summary
  Commit: unknown
  Files: 0
  Bytes: 0
  Dirty: false
  Digest: empty-digest""",
            renderEngineeringContextSnapshotSummary(snapshot, "/repo/output/engineering-context.json")
        )
    }
}
