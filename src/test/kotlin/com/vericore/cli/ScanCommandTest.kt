package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScanCommandTest {

    private fun cliPath(file: File): String = file.absolutePath.replace(File.separatorChar, '/')

    @Test
    fun exposesProductLevelHelp() {
        val result = ScanCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Understand a repository"))
        assertTrue(result.stdout.contains("--path"))
    }

    @Test
    fun rejectsMissingRepositoryPath() {
        val root = Files.createTempDirectory("vericore-scan-missing")
        val error = assertFailsWith<IllegalArgumentException> {
            ScanCommand().test("--path ${root.resolve("does-not-exist")}")
        }
        assertTrue(error.message.orEmpty().contains("Path does not exist"))
    }

    @Test
    fun scanJsonEmitsStandardizedEnvelopeWithScanCommandIdentity() {
        val root = Files.createTempDirectory("vericore-scan-json").toFile()
        try {
            val src = root.resolve("src/main/App.kt")
            src.parentFile.mkdirs()
            src.writeText("package com.example\nclass App\n")

            val result = ScanCommand().test("--path ${cliPath(root)} --json --no-snapshot")
            assertEquals(0, result.statusCode, "Scan should succeed: ${result.stderr}")
            val parsed = Json.parseToJsonElement(result.stdout).jsonObject
            assertEquals("1.0", parsed["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals("scan", parsed["command"]?.jsonPrimitive?.content)
            assertEquals("SUCCESS", parsed["status"]?.jsonPrimitive?.content)
            assertNotNull(parsed["data"])
            assertNotNull(parsed["execution"])
            assertEquals("0", parsed["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }
}
