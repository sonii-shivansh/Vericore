package com.vericore.cli

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaVersionRequirementTest {

    @Test
    fun `runtime is Java 25 or newer`() {
        val major = Runtime.version().feature()
        assertTrue(major >= 25, "Expected Java runtime 25 or newer, but was: $major")
    }

    @Test
    fun `compiled class bytecode has major version 69 (Java 25)`() {
        val classResource = JavaVersionRequirementTest::class.java.name.replace('.', '/') + ".class"
        val bytes = JavaVersionRequirementTest::class.java.classLoader
            .getResourceAsStream(classResource)
            ?.readAllBytes()
            ?: error("Could not load class bytecode for $classResource")

        // First 4 bytes: 0xCAFEBABE
        assertEquals(0xCA.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(0xBE.toByte(), bytes[3])

        // Bytes 6-7: major version (69 for Java 25)
        val majorVersion = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        assertEquals(69, majorVersion, "Expected class bytecode major version 69 (Java 25), but was: $majorVersion")
    }

    @Test
    fun `doctor reports Java 25 as supported`() {
        val directory = Files.createTempDirectory("vericore-doctor-test-").toFile()
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(outContent))
            DoctorCommand().main(arrayOf("--path", directory.absolutePath))
            val output = outContent.toString()
            assertTrue(output.contains("Java 25 (supported)"), "Doctor output should report Java 25: $output")
        } finally {
            System.setOut(originalOut)
            directory.deleteRecursively()
        }
    }
}

