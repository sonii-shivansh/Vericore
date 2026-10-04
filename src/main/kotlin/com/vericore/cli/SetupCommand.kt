package com.vericore.cli

import com.vericore.core.ai.AISetup
import com.vericore.core.ai.AISetupResult
import com.vericore.core.config.UserConfigStore
import com.vericore.core.exceptions.ConfigurationException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class SetupCommand : CliktCommand(
    name = "setup",
    help = "Set up Vericore AI credentials securely on this machine"
) {
    private val provider by option("--provider", help = "AI provider (currently: gemini)").default("gemini")
    private val model by option("--model", help = "Gemini model to use").default(AISetup.DEFAULT_GEMINI_MODEL)
    private val force by option("--force", help = "Replace an existing saved credential").flag()

    override fun run() {
        if (!provider.equals("gemini", ignoreCase = true)) {
            throw ConfigurationException(
                "Unsupported provider: $provider. Supported provider: gemini"
            )
        }

        val existing = UserConfigStore.load()?.ai
        val environmentKey = sequenceOf(
            System.getenv("VERICORE_GEMINI_API_KEY"),
            System.getenv("VERICORE_GOOGLE_API_KEY"),
            System.getenv("GEMINI_API_KEY"),
            System.getenv("GOOGLE_API_KEY")
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        if (!force && existing?.apiKey?.isNotBlank() == true) {
            echo("✓ Gemini is already configured for this user.")
            echo("  Config: ${UserConfigStore.configFile().absolutePath}")
            echo("  Run 'vericore doctor' to verify the setup.")
            return
        }

        if (environmentKey.isBlank() && System.console() == null) {
            throw ConfigurationException(
                "Non-interactive 'setup' requires a Gemini API key via VERICORE_GEMINI_API_KEY, " +
                    "VERICORE_GOOGLE_API_KEY, GEMINI_API_KEY, or GOOGLE_API_KEY."
            )
        }

        val apiKey = environmentKey.ifBlank { readSecret("Gemini API key") }
        if (apiKey.isBlank()) {
            echo("❌ No API key supplied. Nothing was changed.")
            return
        }

        echo("🔐 Validating Gemini credentials...")
        when (val result = AISetup.configureGemini(apiKey, model)) {
            AISetupResult.Success -> {
                echo("✓ Gemini API key validated")
                echo("✓ Saved securely for this user")
                echo("  Config: ${UserConfigStore.configFile().absolutePath}")
                echo("\nYou can now run:")
                echo("  vericore ask \"What is the architecture of this repository?\"")
            }
            is AISetupResult.Failure -> {
                echo("❌ ${result.message}")
                echo("   Nothing was saved.")
            }
        }
    }

    private fun readSecret(label: String): String {
        val console = System.console()
        if (console != null) return console.readPassword("%s: ", label).concatToString().trim()

        echo("⚠️ Secure terminal input is unavailable in this environment.")
        echo("   The key will be visible while typing.")
        echo("$label:", trailingNewline = false)
        return readLine()?.trim().orEmpty()
    }
}
