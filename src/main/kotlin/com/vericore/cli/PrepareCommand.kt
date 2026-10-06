package com.vericore.cli

import com.vericore.core.workflow.EngineeringPreparation
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Creates reusable evidence, plan, and repository-bound change-contract artifacts before implementation. */
class PrepareCommand : CliktCommand(name = "prepare", help = "Prepare an evidence-backed change plan before coding") {
    private val changeSummary by argument("change-summary", help = "Short description of the proposed change")
    private val path by option("--path", help = "Repository path").default(".")
    private val output by option("--output", help = "Preparation artifact path")
    private val planOutput by option("--plan-output", help = "Engineering plan artifact path")
    private val contractOutput by option("--contract-output", help = "Agent change contract artifact path")
    private val plannedPaths by option("--planned-path", help = "Repository-relative path the agent expects to modify").multiple()

    override fun run() {
        val root = File(path).canonicalFile
        val result = runBlocking { EngineeringPreparation.prepare(root.path, changeSummary, plannedPaths) }
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val artifactPath = output ?: root.resolve("output/engineering-context.json").path
        val artifact = File(artifactPath).let { if (it.isAbsolute) it else root.resolve(it.path) }.apply { parentFile?.mkdirs() }
        artifact.writeText(json.encodeToString(result))
        val planPath = planOutput ?: root.resolve("output/engineering-plan.json").path
        val planFile = File(planPath).let { if (it.isAbsolute) it else root.resolve(it.path) }.apply { parentFile?.mkdirs() }
        planFile.writeText(json.encodeToString(result.plan))
        val contractPath = contractOutput ?: root.resolve("output/agent-change-contract.json").path
        val contractFile = File(contractPath).let { if (it.isAbsolute) it else root.resolve(it.path) }.apply { parentFile?.mkdirs() }
        contractFile.writeText(json.encodeToString(result.contract))
        echo("Engineering context: ${artifact.path}")
        echo("Engineering plan: ${planFile.path}")
        echo("Agent change contract: ${contractFile.path}")
        echo("Risk: ${result.plan.riskLevel}")
        echo("Affected components: ${result.plan.affectedComponents.size}")
    }
}
