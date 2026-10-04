package com.vericore.cli

import com.vericore.core.Version
import com.vericore.core.ai.AISetup
import com.vericore.core.ai.AISetupResult
import com.vericore.core.config.ConfigLoader
import com.vericore.core.config.UserConfigStore
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import java.io.File

class DoctorCommand : CliktCommand(
    name = "doctor",
    help = "Check the local Vericore installation and configuration"
) {
    override fun run() {
        var failures = 0
        var warnings = 0

        fun check(label: String, ok: Boolean, detail: String) {
            if (ok) echo("✓ $label — $detail")
            else {
                failures++
                echo("❌ $label — $detail")
            }
        }

        fun warn(label: String, detail: String) {
            warnings++
            echo("⚠ $label — $detail")
        }

        echo("Vericore doctor")
        echo("Version: ${Version.current}")
        echo("")

        val javaMajor = Runtime.version().feature()
        check("Java runtime", javaMajor >= 21, "Java $javaMajor${if (javaMajor >= 21) " (supported)" else " (requires 21+)"}")

        val root = File(".").absoluteFile
        check("Repository", File(root, ".git").exists(), root.absolutePath)

        val canonicalProjectConfig = File(ConfigLoader.DEFAULT_CONFIG_FILE)
        val legacyProjectConfig = File(ConfigLoader.LEGACY_CONFIG_FILE)
        val userConfig = UserConfigStore.load()
        val effective = ConfigLoader.loadEffective()

        val canonicalGeminiEnvironmentKey = System.getenv("VERICORE_GEMINI_API_KEY")?.trim().orEmpty()
        val canonicalGoogleEnvironmentKey = System.getenv("VERICORE_GOOGLE_API_KEY")?.trim().orEmpty()
        val legacyGeminiEnvironmentKey = System.getenv("GEMINI_API_KEY")?.trim().orEmpty()
        val legacyGoogleEnvironmentKey = System.getenv("GOOGLE_API_KEY")?.trim().orEmpty()
        val resolvedApiKey = when {
            canonicalGeminiEnvironmentKey.isNotBlank() -> canonicalGeminiEnvironmentKey
            canonicalGoogleEnvironmentKey.isNotBlank() -> canonicalGoogleEnvironmentKey
            legacyGeminiEnvironmentKey.isNotBlank() -> legacyGeminiEnvironmentKey
            legacyGoogleEnvironmentKey.isNotBlank() -> legacyGoogleEnvironmentKey
            effective.ai.apiKey.isNotBlank() -> effective.ai.apiKey
            else -> ""
        }
        val keySource = when {
            canonicalGeminiEnvironmentKey.isNotBlank() -> "VERICORE_GEMINI_API_KEY environment variable"
            canonicalGoogleEnvironmentKey.isNotBlank() -> "VERICORE_GOOGLE_API_KEY environment variable"
            legacyGeminiEnvironmentKey.isNotBlank() -> "GEMINI_API_KEY environment variable (deprecated; use VERICORE_GEMINI_API_KEY)"
            legacyGoogleEnvironmentKey.isNotBlank() -> "GOOGLE_API_KEY environment variable (deprecated; use VERICORE_GOOGLE_API_KEY)"
            canonicalProjectConfig.isFile && effective.ai.apiKey.isNotBlank() -> "project ${canonicalProjectConfig.name}"
            legacyProjectConfig.isFile && effective.ai.apiKey.isNotBlank() -> "project ${legacyProjectConfig.name} (deprecated; use ${canonicalProjectConfig.name})"
            userConfig?.ai?.apiKey?.isNotBlank() == true -> "user configuration"
            else -> "not configured"
        }
        check("AI provider", effective.ai.provider.isNotBlank(), effective.ai.provider.ifBlank { "not configured" })
        if (resolvedApiKey.isBlank()) {
            warn("AI credentials", "not configured (AI commands require a provider key; run 'vericore setup' to configure one)")
        } else {
            check("AI credentials", true, "configured via $keySource")
            if (legacyGeminiEnvironmentKey.isNotBlank() || legacyGoogleEnvironmentKey.isNotBlank()) {
                warn("Legacy AI configuration", "Deprecated CodeContext environment variable detected; migrate to the VERICORE_* equivalent.")
            }
        }

        if (effective.ai.provider.equals("gemini", ignoreCase = true) && resolvedApiKey.isNotBlank()) {
            echo("   Validating Gemini credentials...")
            when (val result = AISetup.validateGemini(resolvedApiKey, effective.ai.model)) {
                AISetupResult.Success -> echo("✓ Gemini API — reachable and credentials accepted")
                is AISetupResult.Failure -> {
                    failures++
                    echo("❌ Gemini API — ${result.message}")
                }
            }
        }

        echo("")
        when {
            failures > 0 -> {
                echo("❌ Vericore needs attention: $failures error(s), $warnings warning(s).")
                echo("   Fix the errors above, then run 'vericore doctor' again.")
                throw ProgramResult(1)
            }
            warnings > 0 -> {
                echo("⚠ Vericore is ready for deterministic analysis; $warnings optional configuration item(s) need attention.")
                echo("   Run 'vericore setup' if you want to enable AI features.")
            }
            else -> echo("✓ Vericore is ready to use.")
        }
    }
}
