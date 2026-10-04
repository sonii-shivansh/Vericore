package com.vericore.cli

import com.vericore.core.exceptions.VericoreException
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage

object ErrorHandler {
    fun handle(e: Throwable) {
        when (e) {
            is PrintHelpMessage -> throw e // Let Clikt handle help
            is CliktError -> throw e // Let Clikt handle validation errors
            is VericoreException -> {
                System.err.println("❌ ${e.message}")
            }
            else -> {
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
