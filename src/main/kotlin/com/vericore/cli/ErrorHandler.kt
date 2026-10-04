package com.vericore.cli

import com.vericore.core.exceptions.VericoreException
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import mu.KotlinLogging

object ErrorHandler {
    private val logger = KotlinLogging.logger {}

    fun handle(e: Throwable) {
        when (e) {
            is PrintHelpMessage -> throw e // Let Clikt handle help
            is CliktError -> throw e // Let Clikt handle validation errors
            is VericoreException -> {
                logger.debug(e) { "Vericore exception occurred: ${e.message}" }
                System.err.println("❌ ${e.message}")
            }
            else -> {
                logger.error { "Unexpected error occurred: ${e::class.simpleName}: ${e.message}" }
                System.err.println("❌ An unexpected error occurred: ${e.message ?: e::class.simpleName}")
                if (System.getenv("VERICORE_DEBUG") == "1") {
                    e.printStackTrace(System.err)
                } else {
                    System.err.println("   Re-run with VERICORE_DEBUG=1 for a stack trace.")
                }
            }
        }
    }
}
