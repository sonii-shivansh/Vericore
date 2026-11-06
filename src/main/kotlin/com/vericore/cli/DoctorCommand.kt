package com.vericore.cli

import com.vericore.core.Version
import com.vericore.core.ai.AISetup
import com.vericore.core.ai.AISetupResult
import com.vericore.core.config.ConfigLoader
import com.vericore.core.config.UserConfigStore
import com.vericore.core.exceptions.ConfigurationException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DoctorCheckResult(
    val name: String,
    val passed: Boolean,
    val warning: Boolean,
    val detail: String
)

class DoctorCommand : CliktCommand(
    name = "doctor"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Check the local Vericore installation and configuration"
    private val path by option(
        "--path",
        help = "Repository path to inspect; defaults to the current directory"
    ).default(".")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write doctor results as JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save doctor JSON artifact")

    override fun run() {
        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val isJson = outFormat == OutputFormat.JSON

        var failures = 0
        var warnings = 0
        val checks = mutableListOf<DoctorCheckResult>()

        fun check(label: String, ok: Boolean, detail: String) {
            checks += DoctorCheckResult(name = label, passed = ok, warning = false, detail = detail)
            if (ok) {
                if (!isJson) echo("✓ $label — $detail")
            } else {
                failures++
                if (!isJson) echo("❌ $label — $detail")
            }
        }

        fun warn(label: String, detail: String) {
            warnings++
            checks += DoctorCheckResult(name = label, passed = true, warning = true, detail = detail)
            if (!isJson) echo("⚠ $label — $detail")
        }

        if (!isJson) {
            echo("Vericore doctor")
            echo("Version: ${Version.current}")
            echo("")
        }

        val javaMajor = Runtime.version().feature()
        check(
            "Java runtime",
            javaMajor >= 25,
            "Java $javaMajor${if (javaMajor >= 25) " (supported)" else " (requires 25+)"}"
        )

        val root = File(path).canonicalFile
        val pathExists = root.exists()
        check("Diagnostic path", pathExists, root.absolutePath)

        val isGitRepository = pathExists && File(root, ".git").exists()
        if (isGitRepository) {
            check("Repository", true, root.absolutePath)
        } else {
            warn(
                "Repository",
                "not a Git repository at ${root.absolutePath}; repository-specific diagnostics are skipped"
            )
        }

        val canonicalProjectConfig = File(root, ConfigLoader.DEFAULT_CONFIG_FILE)
        val legacyProjectConfig = File(root, ConfigLoader.LEGACY_CONFIG_FILE)
        val userConfig = UserConfigStore.load()
        val effective = ConfigLoader.loadEffective(canonicalProjectConfig.absolutePath)

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
            warn(
                "AI credentials",
                "not configured (AI commands require a provider key; run 'vericore setup' to configure one)"
            )
        } else {
            check("AI credentials", true, "configured via $keySource")
            if (legacyGeminiEnvironmentKey.isNotBlank() || legacyGoogleEnvironmentKey.isNotBlank()) {
                warn(
                    "Legacy AI configuration",
                    "Deprecated CodeContext environment variable detected; migrate to the VERICORE_* equivalent."
                )
            }
        }

        if (effective.ai.provider.equals("gemini", ignoreCase = true) && resolvedApiKey.isNotBlank()) {
            if (!isJson) echo("   Validating Gemini credentials...")
            when (val result = AISetup.validateGemini(resolvedApiKey, effective.ai.model)) {
                AISetupResult.Success -> check("Gemini API", true, "reachable and credentials accepted")
                is AISetupResult.Failure -> {
                    check("Gemini API", false, result.message)
                }
            }
        }

        val status = when {
            failures > 0 -> CommandStatus.FAIL
            warnings > 0 -> CommandStatus.WARNING
            else -> CommandStatus.PASS
        }

        if (isJson) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "doctor",
                status = status,
                summary = "Vericore doctor: ${checks.count { it.passed && !it.warning }} passed, $warnings warning(s), $failures error(s)",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = checks,
                findings = checks.filter { !it.passed || it.warning }.map { c ->
                    EnvelopeFinding(
                        level = if (!c.passed) "ERROR" else "WARNING",
                        message = "${c.name}: ${c.detail}"
                    )
                },
                execution = ExecutionInfo(exitCode = if (failures > 0) 2 else 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
        } else {
            echo("")
            when {
                failures > 0 -> {
                    echo("❌ Vericore needs attention: $failures error(s), $warnings warning(s).")
                    echo("   Fix the errors above, then run 'vericore doctor' again.")
                }
                warnings > 0 -> {
                    echo("⚠ Vericore is ready for deterministic analysis; $warnings optional configuration item(s) need attention.")
                    echo("   Run 'vericore setup' if you want to enable AI features.")
                }
                else -> echo("✓ Vericore is ready to use.")
            }
        }

        if (failures > 0) {
            throw ConfigurationException("Doctor found $failures error(s); see the diagnostics above.")
        }
    }
}
