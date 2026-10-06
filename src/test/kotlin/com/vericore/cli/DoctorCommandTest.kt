package com.vericore.cli

import com.vericore.core.exceptions.ConfigurationException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DoctorCommandTest {
    @Test
    fun `doctor succeeds outside a git repository`() {
        val directory = Files.createTempDirectory("vericore-doctor-").toFile()
        try {
            DoctorCommand().main(arrayOf("--path", directory.absolutePath))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `doctor fails when diagnostic path does not exist`() {
        val directory = Files.createTempDirectory("vericore-doctor-").toFile()
        val missing = directory.resolve("missing")
        try {
            assertFailsWith<ConfigurationException> {
                DoctorCommand().main(arrayOf("--path", missing.absolutePath))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
