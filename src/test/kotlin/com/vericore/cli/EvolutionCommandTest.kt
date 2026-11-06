package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.temporal.CodebaseSnapshot
import com.vericore.core.temporal.InstantSerializer
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.jgit.api.Git

class EvolutionCommandTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun cliPath(file: File): String =
        file.absolutePath.replace(File.separatorChar, '/')

    @Test
    fun rendersSnapshotRowsAndNetGrowthThroughSharedOutput() {
        val snapshots = listOf(
            CodebaseSnapshot(Instant.parse("2026-01-01T12:30:00Z"), "abcdef012345", 10, 120, emptyList()),
            CodebaseSnapshot(Instant.parse("2026-02-01T12:30:00Z"), "123456789abc", 15, 180, emptyList())
        )

        assertEquals(
            """📈 Evolution Report

Snapshots
  Timestamp            | Commit  | Files | Lines
  2026-01-01 12:30 | abcdef0 |    10 | 120
  2026-02-01 12:30 | 1234567 |    15 | 180

Growth
  Net file growth: 50.0% (10 → 15 files)""",
            renderEvolutionReport(snapshots, ZoneId.of("UTC"))
        )
    }

    @Test
    fun preservesEmptyHistoryReportWithoutGrowthSection() {
        assertEquals(
            """📈 Evolution Report

Snapshots
  Timestamp            | Commit  | Files | Lines""",
            renderEvolutionReport(emptyList(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun avoidsDivisionByZeroForEmptyStartingCodebase() {
        val snapshots = listOf(
            CodebaseSnapshot(Instant.parse("2026-01-01T12:30:00Z"), "abcdef012345", 0, 0, emptyList()),
            CodebaseSnapshot(Instant.parse("2026-02-01T12:30:00Z"), "123456789abc", 4, 50, emptyList())
        )
        assertEquals(true, renderEvolutionReport(snapshots, ZoneId.of("UTC")).contains("Net file growth: 0.0% (0 → 4 files)"))
    }

    @Test
    fun snapshotSerializesAndDeserializesRoundTrip() {
        val original = CodebaseSnapshot(
            timestamp = Instant.parse("2026-08-15T10:15:30.123456789Z"),
            commitHash = "abcdef1234567890",
            totalFiles = 42,
            totalLines = 1337,
            topHotspots = listOf("src/Main.kt", "src/Utils.kt")
        )

        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("\"timestamp\":\"2026-08-15T10:15:30.123456789Z\"") || encoded.contains("\"timestamp\": \"2026-08-15T10:15:30.123456789Z\""))
        assertTrue(encoded.contains("abcdef1234567890"))

        val decoded = Json.decodeFromString<CodebaseSnapshot>(encoded)
        assertEquals(original, decoded)
        assertEquals(original.timestamp, decoded.timestamp)
    }

    @Test
    fun evolutionCommandEmitsStandardizedJsonEnvelopeOnGitRepository() {
        val root = Files.createTempDirectory("vericore-evolution-test").toFile()
        try {
            val source = root.resolve("App.kt")
            source.writeText("fun main() = println(\"hello\")\n")

            Git.init().setDirectory(root).call().use { git ->
                git.repository.config.apply {
                    setString("user", null, "name", "Audit Runner")
                    setString("user", null, "email", "audit@example.com")
                    save()
                }
                git.add().addFilepattern("App.kt").call()
                git.commit().setMessage("initial commit").call()
            }

            val result = EvolutionCommand().test("${cliPath(root)} --json")
            assertEquals(0, result.statusCode, "Evolution command should exit 0 on git repo: ${result.stderr}")
            assertTrue(result.stdout.isNotBlank(), "Stdout should contain JSON")

            val parsed = json.parseToJsonElement(result.stdout).jsonObject
            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("evolution", parsed["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", parsed["status"]?.jsonPrimitive?.content)
            assertNotNull(parsed["data"])
            assertEquals("0", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun evolutionCommandSucceedsInHumanModeOnGitRepository() {
        val root = Files.createTempDirectory("vericore-evolution-human-test").toFile()
        try {
            val source = root.resolve("App.kt")
            source.writeText("fun main() = println(\"hello\")\n")

            Git.init().setDirectory(root).call().use { git ->
                git.repository.config.apply {
                    setString("user", null, "name", "Audit Runner")
                    setString("user", null, "email", "audit@example.com")
                    save()
                }
                git.add().addFilepattern("App.kt").call()
                git.commit().setMessage("initial commit").call()
            }

            val result = EvolutionCommand().test(cliPath(root))
            assertEquals(0, result.statusCode)
            assertTrue(result.stdout.contains("Evolution Report"))
            assertTrue(result.stdout.contains("Snapshots"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun evolutionCommandRefusesNonDirectory() {
        val file = Files.createTempFile("vericore-not-dir", ".txt").toFile()
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                EvolutionCommand().test(cliPath(file))
            }
            assertTrue(error.message.orEmpty().contains("not a directory"))
        } finally {
            file.delete()
        }
    }
}
