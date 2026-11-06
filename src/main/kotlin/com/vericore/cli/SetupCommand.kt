package com.vericore.cli

import com.vericore.core.ai.AISetup
import com.vericore.core.ai.AISetupResult
import com.vericore.core.config.UserConfigStore
import com.vericore.core.exceptions.ConfigurationException
import com.vericore.core.exceptions.ValidationException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class SetupCommand : CliktCommand(
    name = "setup"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Set up Vericore AI credentials securely on this machine"
    private val provider by option("--provider", help = "AI provider (currently: gemini)").default("gemini")
    private val model by option("--model", help = "Gemini model to use").default(AISetup.DEFAULT_GEMINI_MODEL)
    private val force by option("--force", help = "Replace an existing saved credential").flag()
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write setup result as JSON to stdout (alias for --format json)").flag()
    private val apiKeyOption by option("--api-key", help = "Gemini API key (defaults to environment)").default("")

    override fun run() {
        if (!provider.equals("gemini", ignoreCase = true)) {
            throw ConfigurationException(
                "Unsupported provider: $provider. Supported provider: gemini"
            )
        }

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val isJson = outFormat == OutputFormat.JSON

        val existing = UserConfigStore.load()?.ai
        val environmentKey = apiKeyOption.ifBlank {
            sequenceOf(
                System.getenv("VERICORE_GEMINI_API_KEY"),
                System.getenv("VERICORE_GOOGLE_API_KEY"),
                System.getenv("GEMINI_API_KEY"),
                System.getenv("GOOGLE_API_KEY")
            ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        }

        if (!force && existing?.apiKey?.isNotBlank() == true) {
            if (isJson) {
                val envelope = CommandEnvelope(
                    schemaVersion = "1.0",
                    command = "setup",
                    status = CommandStatus.ALREADY_INITIALIZED,
                    summary = "Gemini is already configured for this user",
                    data = mapOf("model" to (existing.model ?: model)),
                    artifacts = listOf(
                        EnvelopeArtifact("config", UserConfigStore.configFile().absolutePath)
                    ),
                    nextStep = "vericore doctor",
                    execution = ExecutionInfo(exitCode = 0)
                )
                echo(JsonEnvelope.encode(envelope))
                return
            }
            echo("✓ Gemini is already configured for this user.")
            echo("  Config: ${UserConfigStore.configFile().absolutePath}")
            echo("  Run 'vericore doctor' to verify the setup.")
            return
        }

        val apiKey = environmentKey.ifBlank {
            if (isJson) "" else readSecret("Gemini API key")
        }
        if (apiKey.isBlank()) {
            if (isJson) {
                val envelope = CommandEnvelope(
                    schemaVersion = "1.0",
                    command = "setup",
                    status = CommandStatus.WARNING,
                    summary = "No API key supplied. Nothing was changed.",
                    data = mapOf("model" to model),
                    execution = ExecutionInfo(exitCode = 0)
                )
                echo(JsonEnvelope.encode(envelope))
                return
            }
            echo("❌ No API key supplied. Nothing was changed.")
            return
        }

        if (!isJson) echo("🔐 Validating Gemini credentials...")
        when (val result = AISetup.configureGemini(apiKey, model)) {
            AISetupResult.Success -> {
                if (isJson) {
                    val envelope = CommandEnvelope(
                        schemaVersion = "1.0",
                        command = "setup",
                        status = CommandStatus.PASS,
                        summary = "Gemini API key validated and saved securely",
                        data = mapOf("model" to model),
                        artifacts = listOf(
                            EnvelopeArtifact("config", UserConfigStore.configFile().absolutePath)
                        ),
                        nextStep = "vericore ask \"What is the architecture of this repository?\"",
                        execution = ExecutionInfo(exitCode = 0)
                    )
                    echo(JsonEnvelope.encode(envelope))
                    return
                }
                echo("✓ Gemini API key validated")
                echo("✓ Saved securely for this user")
                echo("  Config: ${UserConfigStore.configFile().absolutePath}")
                echo("\nYou can now run:")
                echo("  vericore ask \"What is the architecture of this repository?\"")
            }
            is AISetupResult.Failure -> {
                if (isJson) {
                    val envelope = CommandEnvelope(
                        schemaVersion = "1.0",
                        command = "setup",
                        status = CommandStatus.FAIL,
                        summary = "Gemini validation failed: ${result.message}",
                        data = mapOf("model" to model),
                        findings = listOf(
                            EnvelopeFinding(level = "ERROR", message = result.message)
                        ),
                        execution = ExecutionInfo(exitCode = 1)
                    )
                    echo(JsonEnvelope.encode(envelope))
                    throw ValidationException("Gemini validation failed: ${result.message}")
                }
                echo("❌ ${result.message}")
                echo("   Nothing was saved.")
                throw ValidationException("Gemini validation failed: ${result.message}")
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
