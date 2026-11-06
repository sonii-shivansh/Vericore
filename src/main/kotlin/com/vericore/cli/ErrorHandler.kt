package com.vericore.cli

import com.vericore.core.exceptions.ConfigurationException
import com.vericore.core.exceptions.ValidationException
import com.vericore.core.exceptions.VericoreException
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import io.github.oshai.kotlinlogging.KotlinLogging

object ErrorHandler {
    private val logger = KotlinLogging.logger {}

    fun handle(e: Throwable, args: Array<String> = emptyArray()): Int {
        val exitCode = when (e) {
            is PrintHelpMessage -> throw e // Let Clikt handle help
            is CliktError -> throw e // Let Clikt handle validation errors
            is ValidationException -> {
                logger.debug(e) { "Validation failure: ${e.message}" }
                System.err.println("❌ ${e.message}")
                1
            }
            is ConfigurationException -> {
                logger.debug(e) { "Configuration error: ${e.message}" }
                System.err.println("❌ ${e.message}")
                2
            }
            is IllegalArgumentException -> {
                logger.debug(e) { "Invalid argument: ${e.message}" }
                System.err.println("❌ ${e.message}")
                2
            }
            is VericoreException -> {
                logger.debug(e) { "Vericore exception occurred: ${e.message}" }
                System.err.println("❌ ${e.message}")
                1
            }
            else -> {
                logger.error(e) { "Unexpected error occurred" }
                System.err.println("❌ An unexpected error occurred: ${e.message}")
                System.err.println("   Check the logs for more details.")
                3
            }
        }

        val requestsJson = args.any {
            it == "--json" || it == "-j" ||
            it.equals("--format=json", ignoreCase = true)
        } || (args.indexOf("--format").let { it >= 0 && it + 1 < args.size && args[it + 1].equals("json", ignoreCase = true) })

        if (requestsJson && !JsonEnvelope.emitted) {
            val commandName = args.firstOrNull { !it.startsWith("-") } ?: "vericore"
            val errorEnvelope = CommandEnvelope<Map<String, String>>(
                schemaVersion = "1.0",
                command = commandName,
                status = if (exitCode == 1) CommandStatus.FAIL else CommandStatus.ERROR,
                summary = e.message ?: "Execution failed",
                findings = listOf(
                    EnvelopeFinding(
                        level = "ERROR",
                        message = e.message ?: "Execution failed"
                    )
                ),
                execution = ExecutionInfo(exitCode = exitCode)
            )
            println(JsonEnvelope.encode(errorEnvelope))
        }

        return exitCode
    }
}
