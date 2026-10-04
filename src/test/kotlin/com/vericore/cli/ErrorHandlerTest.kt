package com.vericore.cli

import com.vericore.core.exceptions.VericoreException
import com.vericore.core.exceptions.ValidationException
import com.vericore.core.exceptions.ConfigurationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertTrue

class ErrorHandlerTest {
    private val standardErr = System.err
    private val outputStreamCaptor = ByteArrayOutputStream()

    @BeforeEach
    fun setUp() {
        System.setErr(PrintStream(outputStreamCaptor))
    }

    @AfterEach
    fun tearDown() {
        System.setErr(standardErr)
    }

    @Test
    fun `handle should print friendly message for VericoreException`() {
        val exception = ConfigurationException("Config error")
        ErrorHandler.handle(exception)

        System.err.flush()
        val output = outputStreamCaptor.toString()
        assertTrue(output.contains("❌ Config error"), "Should contain friendly error message. Got: '$output'")
    }

    @Test
    fun `handle should keep validation failures stack-trace free`() {
        ErrorHandler.handle(ValidationException("Architecture contract failed"))

        System.err.flush()
        val output = outputStreamCaptor.toString()
        assertTrue(output.contains("❌ Architecture contract failed"))
        assertTrue(!output.contains("ValidationException"))
        assertTrue(!output.contains("\tat "))
    }

    @Test
    fun `handle should print generic message for unknown exceptions`() {
        val exception = RuntimeException("Unknown error")
        ErrorHandler.handle(exception)

        System.err.flush()
        val output = outputStreamCaptor.toString()
        assertTrue(output.contains("❌ An unexpected error occurred: Unknown error"), "Should contain generic error message. Got: '$output'")
    }
}
