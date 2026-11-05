package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CliEnvelopeContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun cliPath(file: File): String = file.absolutePath.replace('\\', '/')

    @Test
    fun `doctor command emits standardized envelope in json mode`() {
        val tempDir = Files.createTempDirectory("vericore-envelope-doctor").toFile()
        try {
            val result = DoctorCommand().test("--path ${cliPath(tempDir)} --json")
            assertEquals(0, result.statusCode)
            val parsed = json.parseToJsonElement(result.stdout).jsonObject

            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("doctor", parsed["command"]?.jsonPrimitive?.content)
            assertNotNull(parsed["status"]?.jsonPrimitive?.content)
            assertTrue(parsed["summary"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertNotNull(parsed["data"])
            assertNotNull(parsed["execution"])
            assertEquals("0", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `init command emits standardized product json in json mode`() {
        val tempDir = Files.createTempDirectory("vericore-envelope-init").toFile()
        try {
            File(tempDir, ".git").mkdirs()
            val result = InitCommand().test("--path ${cliPath(tempDir)} --json")
            assertEquals(0, result.statusCode)
            val parsed = json.parseToJsonElement(result.stdout).jsonObject

            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("init", parsed["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", parsed["status"]?.jsonPrimitive?.content)
            assertTrue(parsed["summary"]?.jsonPrimitive?.content?.contains("initialized") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `setup command emits standardized envelope in json mode`() {
        val result = SetupCommand().test("--json")
        assertEquals(0, result.statusCode)
        val parsed = json.parseToJsonElement(result.stdout).jsonObject

        assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
        assertEquals("setup", parsed["command"]?.jsonPrimitive?.content)
        assertNotNull(parsed["status"]?.jsonPrimitive?.content)
        assertNotNull(parsed["summary"]?.jsonPrimitive?.content)
        assertNotNull(parsed["execution"])
    }

    @Test
    fun `mcp-config command emits client-ready configuration`() {
        val tempDir = Files.createTempDirectory("vericore-envelope-mcp").toFile()
        try {
            val result = McpConfigCommand(tempDir).test("")
            assertEquals(0, result.statusCode)
            val parsed = json.parseToJsonElement(result.stdout).jsonObject
            assertNotNull(parsed["mcpServers"])

            val writeResult = McpConfigCommand(tempDir).test("--write")
            assertEquals(0, writeResult.statusCode)
            assertTrue(tempDir.resolve(".mcp.json").isFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `command envelope utility correctly relativizes paths`() {
        val tempDir = Files.createTempDirectory("vericore-relativize").toFile()
        try {
            val root = File(tempDir, "repo").canonicalFile
            val fileInside = File(root, "src/main/App.kt").canonicalFile
            val fileOutside = File(tempDir, "other/Other.kt").canonicalFile

            assertEquals("src/main/App.kt", repositoryRelativePath(fileInside.path, root))
            assertEquals(fileOutside.path.replace('\\', '/'), repositoryRelativePath(fileOutside.path, root))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
