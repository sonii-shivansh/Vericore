package com.vericore.cli

import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.workflow.AgentChangeContract
import com.vericore.core.workflow.EngineeringVerification
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Verifies the working-tree change against the exact contract persisted by prepare. */
class VerifyCommand : CliktCommand(name = "verify", help = "Verify the current change against an engineering plan and immutable change contract") {
    private val path by option("--path", help = "Repository path").default(".")
    private val planFile by option("--plan", help = "Engineering plan JSON artifact")
    private val contractFile by option("--contract", help = "Agent change contract JSON artifact")
    private val output by option("--output", help = "Optional verification artifact path")

    override fun run() {
        val root = File(path).canonicalFile
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        val planPath = planFile ?: root.resolve("output/engineering-plan.json").path
        val resolvedPlan = File(planPath).let { if (it.isAbsolute) it else root.resolve(it.path) }
        val contractPath = contractFile ?: root.resolve("output/agent-change-contract.json").path
        val resolvedContract = File(contractPath).let { if (it.isAbsolute) it else root.resolve(it.path) }
        require(resolvedContract.isFile) { "Immutable agent change contract not found: ${resolvedContract.path}. Run prepare before verify." }
        val plan = json.decodeFromString<EngineeringPlan>(resolvedPlan.readText())
        val contract = json.decodeFromString<AgentChangeContract>(resolvedContract.readText())
        val result = runBlocking { EngineeringVerification.verify(root.path, plan, contract) }
        val encoded = json.encodeToString(result)
        if (output != null) {
            val file = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }.apply { parentFile?.mkdirs() }
            file.writeText(encoded)
            echo("Verification report: ${file.path}")
        } else {
            echo(encoded)
        }
        echo("Status: ${result.status}")
        if (result.status == com.vericore.core.workflow.SafetyStatus.FAIL) {
            echo("❌ Change verification failed: the prepared contract or change scope is invalid")
            throw ProgramResult(1)
        }
    }
}
