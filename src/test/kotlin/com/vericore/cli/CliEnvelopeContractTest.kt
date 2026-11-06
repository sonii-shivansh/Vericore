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
            assertNotNull(parsed["execution"])
            assertEquals("0", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)
            assertNotNull(parsed["data"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scan command identifies itself as scan while analyze identifies as analyze`() {
        val tempDir = Files.createTempDirectory("vericore-envelope-scan-analyze").toFile()
        try {
            val src = File(tempDir, "src/main/Example.kt")
            src.parentFile.mkdirs()
            src.writeText("package com.example\nclass Example\n")

            val scanResult = ScanCommand().test("--path ${cliPath(tempDir)} --json --no-snapshot")
            assertEquals(0, scanResult.statusCode)
            val scanParsed = json.parseToJsonElement(scanResult.stdout).jsonObject
            assertEquals("1.0", scanParsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("scan", scanParsed["command"]?.jsonPrimitive?.content)
            assertEquals("SUCCESS", scanParsed["status"]?.jsonPrimitive?.content)

            val analyzeResult = ImprovedAnalyzeCommand().test("${cliPath(tempDir)} --json --no-snapshot")
            assertEquals(0, analyzeResult.statusCode)
            val analyzeParsed = json.parseToJsonElement(analyzeResult.stdout).jsonObject
            assertEquals("1.0", analyzeParsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("analyze", analyzeParsed["command"]?.jsonPrimitive?.content)
            assertEquals("SUCCESS", analyzeParsed["status"]?.jsonPrimitive?.content)
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

    @Test
    fun `output format resolve accepts valid formats and rejects invalid or conflicting values`() {
        assertEquals(OutputFormat.HUMAN, OutputFormat.resolve("human"))
        assertEquals(OutputFormat.HUMAN, OutputFormat.resolve(null))
        assertEquals(OutputFormat.HUMAN, OutputFormat.resolve(""))
        assertEquals(OutputFormat.JSON, OutputFormat.resolve("json"))
        assertEquals(OutputFormat.JSON, OutputFormat.resolve("JSON"))
        assertEquals(OutputFormat.JSON, OutputFormat.resolve(null, jsonFlag = true))

        val exXml = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            OutputFormat.resolve("xml")
        }
        assertTrue(exXml.message?.contains("Unsupported output format: 'xml'") == true)

        val exXmlWithJson = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            OutputFormat.resolve("xml", jsonFlag = true)
        }
        assertTrue(exXmlWithJson.message?.contains("Unsupported output format: 'xml'") == true)

        val exInvalid = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            OutputFormat.resolve("invalid")
        }
        assertTrue(exInvalid.message?.contains("Unsupported output format: 'invalid'") == true)

        val exConflict = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            OutputFormat.resolve("human", jsonFlag = true)
        }
        assertTrue(exConflict.message?.contains("Conflicting output format options") == true)
    }

    @Test
    fun `mcp-config command validates output format and rejects invalid formats`() {
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            McpConfigCommand().test(listOf("--format", "xml"))
        }
        assertTrue(ex.message?.contains("Unsupported output format: 'xml'") == true)
    }

    @Test
    fun `error handler emits structured json error when json is requested and post-parsing error occurs`() {
        JsonEnvelope.reset()
        val originalOut = System.out
        val originalErr = System.err
        val outStream = java.io.ByteArrayOutputStream()
        val errStream = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(outStream))
            System.setErr(java.io.PrintStream(errStream))

            val ex = com.vericore.core.exceptions.ValidationException("Contract validation failed")
            val exitCode = ErrorHandler.handle(ex, arrayOf("architecture-contract", "--format", "json"))

            assertEquals(1, exitCode)
            val stdoutText = outStream.toString().trim()
            val parsed = json.parseToJsonElement(stdoutText).jsonObject
            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("architecture-contract", parsed["command"]?.jsonPrimitive?.content)
            assertEquals("FAIL", parsed["status"]?.jsonPrimitive?.content)
            assertEquals("Contract validation failed", parsed["summary"]?.jsonPrimitive?.content)
            assertEquals("1", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)

            val stderrText = errStream.toString()
            assertTrue(stderrText.contains("Contract validation failed"))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            JsonEnvelope.reset()
        }
    }
}
