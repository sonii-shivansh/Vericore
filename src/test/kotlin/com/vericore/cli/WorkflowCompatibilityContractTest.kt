package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.exceptions.ConfigurationException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * End-to-end regression tests verifying that CI/CD workflows, automation scripts,
 * and external consumers can reliably parse and interact with standardized CLI outputs.
 */
class WorkflowCompatibilityContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun cliPath(file: File): String = file.absolutePath.replace('\\', '/')

    @Test
    fun `universal envelope schema contains all required contract fields`() {
        val tempDir = Files.createTempDirectory("vericore-compat-doctor").toFile()
        try {
            val result = DoctorCommand().test("--path ${cliPath(tempDir)} --json")
            assertEquals(0, result.statusCode, "Doctor command must exit with code 0")

            val parsed = json.parseToJsonElement(result.stdout).jsonObject
            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("doctor", parsed["command"]?.jsonPrimitive?.content)
            assertTrue(parsed["status"]?.jsonPrimitive?.content in listOf("PASS", "WARNING", "FAIL"))
            assertNotNull(parsed["summary"]?.jsonPrimitive?.content)
            assertNotNull(parsed["execution"])
            assertEquals("0", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)

            // Verify data extraction contract
            assertNotNull(parsed["data"], "Envelope must contain payload in data field")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `human-readable output mode does not emit json envelope`() {
        val tempDir = Files.createTempDirectory("vericore-compat-human").toFile()
        try {
            val result = DoctorCommand().test("--path ${cliPath(tempDir)}")
            assertEquals(0, result.statusCode)
            val stdout = result.stdout.trim()

            // Human output must not start with JSON opening brace
            assertFalse(stdout.startsWith("{"), "Human output must not be JSON formatted")
            assertTrue(stdout.contains("Vericore doctor"), "Human output must contain header text")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `conflicting output format flags are rejected with exit code 2`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            DoctorCommand().test("--format human --json")
        }
        assertTrue(ex.message?.contains("Conflicting output format options") == true)
        val exitCode = ErrorHandler.handle(ex, arrayOf("doctor", "--format", "human", "--json"))
        assertEquals(2, exitCode, "Conflicting flags must exit with code 2 (usage/validation error)")
    }

    @Test
    fun `unsupported output format is rejected with exit code 2`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            DoctorCommand().test("--format xml")
        }
        assertTrue(ex.message?.contains("Unsupported output format: 'xml'") == true)
        val exitCode = ErrorHandler.handle(ex, arrayOf("doctor", "--format", "xml"))
        assertEquals(2, exitCode, "Invalid format must exit with code 2")
    }

    @Test
    fun `mcp-config outputs raw client configuration without envelope wrapper`() {
        val tempDir = Files.createTempDirectory("vericore-compat-mcp-cfg").toFile()
        try {
            val result = McpConfigCommand(tempDir).test("")
            assertEquals(0, result.statusCode)

            val parsed = json.parseToJsonElement(result.stdout).jsonObject
            // Must have mcpServers at root, NOT wrapped in CommandEnvelope
            assertNotNull(parsed["mcpServers"], "mcp-config must output raw editor JSON with mcpServers key")
            assertNull(parsed["schemaVersion"], "mcp-config must not wrap client config in CommandEnvelope")
            assertNull(parsed["command"], "mcp-config must not contain envelope command field")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `context-diff unboxes both raw domain snapshots and envelope-wrapped snapshots`() {
        val tempDir = Files.createTempDirectory("vericore-compat-diff").toFile()
        try {
            val rawSnapshotFile = File(tempDir, "raw-snap.json")
            val wrappedSnapshotFile = File(tempDir, "wrapped-snap.json")

            val rawSnapshotJson = """
                {
                  "schemaVersion": "1.0",
                  "repositoryCommit": "a1b2c3d4",
                  "files": [],
                  "totalFiles": 0,
                  "totalBytes": 0,
                  "languages": [],
                  "dirty": false,
                  "changedPaths": [],
                  "snapshotDigest": "digest123"
                }
            """.trimIndent()
            rawSnapshotFile.writeText(rawSnapshotJson)

            val wrappedSnapshotJson = """
                {
                  "schemaVersion": "1.0",
                  "command": "context-snapshot",
                  "status": "PASS",
                  "summary": "Engineering context snapshot",
                  "data": $rawSnapshotJson,
                  "execution": { "exitCode": 0 }
                }
            """.trimIndent()
            wrappedSnapshotFile.writeText(wrappedSnapshotJson)

            // Test diff between raw and wrapped snapshots
            val result = EngineeringContextDiffCommand().test("${cliPath(rawSnapshotFile)} ${cliPath(wrappedSnapshotFile)} --json")
            assertEquals(0, result.statusCode, "context-diff must succeed when unboxing mixed raw/wrapped snapshots")

            val parsed = json.parseToJsonElement(result.stdout).jsonObject
            assertEquals("context-diff", parsed["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", parsed["status"]?.jsonPrimitive?.content)
            assertNotNull(parsed["data"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `error envelope is emitted on validation failure in json mode`() {
        val nonExistentDir = File("non/existent/path/for/test/12345")
        val ex = assertFailsWith<ConfigurationException> {
            DoctorCommand().test("--path ${cliPath(nonExistentDir)} --json")
        }
        val exitCode = ErrorHandler.handle(ex, arrayOf("doctor", "--path", cliPath(nonExistentDir), "--json"))
        assertEquals(2, exitCode, "Configuration error must exit with code 2")
    }

    @Test
    fun `command status constants align with expected lifecycle values`() {
        assertEquals("PASS", CommandStatus.PASS)
        assertEquals("FAIL", CommandStatus.FAIL)
        assertEquals("WARNING", CommandStatus.WARNING)
        assertEquals("REVIEW_REQUIRED", CommandStatus.REVIEW_REQUIRED)
        assertEquals("NO_CHANGES", CommandStatus.NO_CHANGES)
        assertEquals("ERROR", CommandStatus.ERROR)
    }
}
