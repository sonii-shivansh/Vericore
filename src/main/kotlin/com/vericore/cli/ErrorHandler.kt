package com.vericore.cli

import com.vericore.core.exceptions.ConfigurationException
import com.vericore.core.exceptions.ValidationException
import com.vericore.core.exceptions.VericoreException
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import mu.KotlinLogging

object ErrorHandler {
    private val logger = KotlinLogging.logger {}

    fun handle(e: Throwable): Int {
        when (e) {
            is PrintHelpMessage -> throw e // Let Clikt handle help
            is CliktError -> throw e // Let Clikt handle validation errors
            is ValidationException -> {
                logger.debug(e) { "Validation failure: ${e.message}" }
                System.err.println("❌ ${e.message}")
                return 1
            }
            is ConfigurationException -> {
                logger.debug(e) { "Configuration error: ${e.message}" }
                System.err.println("❌ ${e.message}")
                return 2
            }
            is IllegalArgumentException -> {
                logger.debug(e) { "Invalid argument: ${e.message}" }
                System.err.println("❌ ${e.message}")
                return 2
            }
            is VericoreException -> {
                logger.debug(e) { "Vericore exception occurred: ${e.message}" }
                System.err.println("❌ ${e.message}")
                return 1
            }
            else -> {
                logger.error(e) { "Unexpected error occurred" }
                System.err.println("❌ An unexpected error occurred: ${e.message}")
                System.err.println("   Check the logs for more details.")
                return 3
            }
        }
    }
}
