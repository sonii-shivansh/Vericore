package com.vericore.cli

import com.vericore.core.Version
import com.vericore.core.config.ConfigLoader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

class InitCommand : CliktCommand(name = "init", help = "Initialize Vericore for a repository") {
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val force by option("--force", help = "Replace an existing Vericore project configuration").flag()
    private val jsonOutput by option("--json", help = "Write the product result as JSON").flag()

    override fun run() {
        val root = File(path).canonicalFile
        require(root.exists()) { "Repository path does not exist: ${root.path}" }
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }
        require(root.resolve(".git").exists()) { "Not a Git repository: ${root.path}" }
        val configFile = root.resolve(ConfigLoader.DEFAULT_CONFIG_FILE)
        if (configFile.exists() && !force) {
            val result = ProductCommandResult(
                command = "init",
                status = "ALREADY_INITIALIZED",
                repository = root.path,
                artifacts = listOf(ProductArtifact("config", configFile.path)),
                nextStep = "vericore doctor --path ${root.path}"
            )
            if (jsonOutput) {
                echo(productJson(result))
            } else {
                echo(
                    HumanCommandOutput.render(
                        title = "VERICORE — PROJECT INITIALIZED",
                        status = "ALREADY_INITIALIZED",
                        sections = listOf(
                            HumanCommandOutput.section("Repository", root.name, root.path),
                            HumanCommandOutput.section("Configuration", configFile.path)
                        ),
                        nextStep = "vericore doctor --path ${root.path}"
                    )
                )
            }
            return
        }

        ConfigLoader.createDefault(configFile.path)
        listOf(
            root.resolve(".vericore"),
            root.resolve(".vericore/sessions"),
            root.resolve(".vericore/reports"),
            root.resolve(".vericore/evidence"),
            root.resolve(".vericore/baselines"),
            root.resolve(".vericore/contracts")
        ).forEach { it.mkdirs() }

        val result = ProductCommandResult(
            command = "init",
            status = "PASS",
            repository = root.path,
            artifacts = listOf(
                ProductArtifact("config", configFile.path),
                ProductArtifact("workspace", root.resolve(".vericore").path)
            ),
            nextStep = "vericore scan --path ${root.path}"
        )
        if (jsonOutput) {
            echo(productJson(result))
            return
        }

        echo(
            HumanCommandOutput.render(
                title = "VERICORE — PROJECT INITIALIZED",
                status = result.status,
                sections = listOf(
                    HumanCommandOutput.section("Repository", root.name, root.path),
                    HumanCommandOutput.section(
                        "Initialized",
                        "✓ ${ConfigLoader.DEFAULT_CONFIG_FILE}",
                        "✓ .vericore/ session and evidence workspace",
                        "✓ deterministic analysis defaults"
                    ),
                    HumanCommandOutput.section("AI", "Optional — configure with: vericore setup"),
                    HumanCommandOutput.section("MCP", "Client configuration: vericore mcp-config"),
                    HumanCommandOutput.section("Version", "Vericore ${Version.current} is ready.")
                ),
                nextStep = "vericore scan --path ${root.path}"
            )
        )
    }
}