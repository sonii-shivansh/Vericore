package com.vericore.cli

import com.vericore.core.Version
import com.vericore.core.config.ConfigLoader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

class InitCommand : CliktCommand(
    name = "init",
    help = "Initialize Vericore for a repository"
) {
    private val path by option("--path", help = "Repository path (default: current directory)").default(".")
    private val force by option("--force", help = "Replace an existing Vericore project configuration").flag()

    override fun run() {
        val root = File(path).canonicalFile
        require(root.exists()) { "Repository path does not exist: ${root.path}" }
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }
        require(root.resolve(".git").exists()) { "Not a Git repository: ${root.path}" }

        val configFile = root.resolve(ConfigLoader.DEFAULT_CONFIG_FILE)
        if (configFile.exists() && !force) {
            echo("✓ Vericore is already initialized.")
            echo("  Config: ${configFile.path}")
            echo("  Run 'vericore doctor' to verify the setup.")
            return
        }

        ConfigLoader.createDefault(configFile.path)
        listOf(root.resolve(".vericore"), root.resolve(".vericore/sessions"), root.resolve(".vericore/reports"), root.resolve(".vericore/evidence"), root.resolve(".vericore/baselines"), root.resolve(".vericore/contracts")).forEach { it.mkdirs() }

        echo("")
        echo("╭──────────────────────────────────────────────╮")
        echo("│ VERICORE — PROJECT INITIALIZED               │")
        echo("╰──────────────────────────────────────────────╯")
        echo("")
        echo("Repository")
        echo("  ${root.name}")
        echo("  ${root.path}")
        echo("")
        echo("Initialized")
        echo("  ✓ ${ConfigLoader.DEFAULT_CONFIG_FILE}")
        echo("  ✓ .vericore/ session and evidence workspace")
        echo("  ✓ deterministic analysis defaults")
        echo("")
        echo("AI")
        echo("  Optional — configure with: vericore setup")
        echo("")
        echo("MCP")
        echo("  Client configuration: vericore mcp-config")
        echo("")
        echo("Next step")
        echo("  vericore analyze ${root.path}")
        echo("")
        echo("Vericore ${Version.current} is ready.")
    }
}
