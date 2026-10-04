package com.vericore.cli

import com.vericore.core.exceptions.ConfigurationException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DoctorCommandTest {
    @Test
    fun `doctor exits with failure when repository check fails`() {
        val directory = Files.createTempDirectory("vericore-doctor-").toFile()
        try {
            assertFailsWith<ConfigurationException> {
                DoctorCommand { directory }.run()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
