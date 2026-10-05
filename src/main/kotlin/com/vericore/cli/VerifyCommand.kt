package com.vericore.cli

import com.vericore.core.intelligence.ChangeType
import com.vericore.core.intelligence.ChangedFile
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.workflow.AgentChangeContract
import com.vericore.core.workflow.ChangeSafetyAnalyzer
import com.vericore.core.workflow.EngineeringVerification
import com.vericore.core.workflow.RepositoryState
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContractOnlyVerificationResult(
    val schemaVersion: String = "1.0",
    val repository: String,
    val valid: Boolean,
    val status: String,
    val reasons: List<String>,
    val changedPaths: List<String>
)

/** Verifies the working-tree change against an engineering plan and immutable change contract. */
class VerifyCommand : CliktCommand(name = "verify", help = "Verify the current change against an engineering plan and immutable change contract") {
    private val path by option("--path", help = "Repository path").default(".")
    private val planFile by option("--plan", help = "Engineering plan JSON artifact")
    private val contractFile by option("--contract", help = "Agent change contract JSON artifact")
    private val output by option("--output", help = "Optional verification artifact path")
    private val contractOnly by option("--contract-only", help = "Validate contract, repository identity, HEAD, and planned working-tree changes without rebuilding the full analysis graph").flag(default = false)

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

        if (contractOnly) {
            verifyContractOnly(root, plan, contract, json)
            return
        }

        val result = runBlocking { EngineeringVerification.verify(root.path, plan, contract) }
        val encoded = json.encodeToString(result)
        writeOutput(encoded)
        echo("Status: ${result.status}")
        if (result.status == com.vericore.core.workflow.SafetyStatus.FAIL) {
            throw IllegalStateException("Change verification failed: the prepared contract or change scope is invalid")
        }
    }

    private fun verifyContractOnly(root: File, plan: EngineeringPlan, contract: AgentChangeContract, json: Json) {
        val reasons = buildList {
            val expectedContractFingerprint = AgentChangeContract.fingerprintFor(contract)
            if (contract.fingerprint != expectedContractFingerprint) add("The persisted agent change contract fingerprint is invalid or tampered.")
            if (contract.repository != root.path) add("The contract belongs to a different repository: ${contract.repository}")
            if (plan.contractFingerprint != contract.fingerprint) add("The engineering plan is not bound to the persisted contract fingerprint.")
            val expectedPlanFingerprint = AgentChangeContract.fromPlan(plan, root.path, contract.preparedHead).fingerprint
            if (expectedPlanFingerprint != contract.fingerprint) add("The supplied plan does not match the persisted contract contents.")
            val currentHead = RepositoryState.head(root.path).orEmpty()
            if (contract.preparedHead.isNotBlank() && currentHead.isNotBlank() && contract.preparedHead != currentHead) {
                add("The repository HEAD changed after prepare (${contract.preparedHead} -> $currentHead); the contract is stale.")
            }
        }

        val changes = collectContractOnlyChanges(root)
        val safety = ChangeSafetyAnalyzer.verify(changes, plan.plannedPaths)
        val allReasons = reasons + safety.reasons.filter { it != "No source working-tree changes were detected." }
        val status = when {
            reasons.isNotEmpty() || safety.status == com.vericore.core.workflow.SafetyStatus.FAIL -> com.vericore.core.workflow.SafetyStatus.FAIL
            safety.status == com.vericore.core.workflow.SafetyStatus.REVIEW_REQUIRED -> com.vericore.core.workflow.SafetyStatus.REVIEW_REQUIRED
            else -> com.vericore.core.workflow.SafetyStatus.PASS
        }
        val result = ContractOnlyVerificationResult(
            repository = root.path,
            valid = status == com.vericore.core.workflow.SafetyStatus.PASS,
            status = status.name,
            reasons = allReasons,
            changedPaths = safety.changedPaths
        )
        writeOutput(json.encodeToString(result))
        echo("Status: ${result.status}")
        if (status == com.vericore.core.workflow.SafetyStatus.FAIL) {
            throw IllegalStateException("Change verification failed: the prepared contract or change scope is invalid")
        }
    }

    private fun collectContractOnlyChanges(root: File): List<ChangedFile> {
        val changes = mutableListOf<ChangedFile>()
        parseGitNameStatus(root, listOf("diff", "--cached", "--name-status", "--diff-filter=ACDMRT")).forEach { changes += it }
        parseGitNameStatus(root, listOf("diff", "--name-status", "--diff-filter=ACDMRT")).forEach { changes += it }
        runGit(root, listOf("ls-files", "--others", "--exclude-standard")).lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { path -> changes += ChangedFile(path.replace('\\', '/'), ChangeType.ADDED) }
        return changes.distinctBy { Triple(it.path, it.changeType, it.oldPath) }
    }

    private fun parseGitNameStatus(root: File, args: List<String>): List<ChangedFile> =
        runGit(root, args).lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val status = parts[0].firstOrNull() ?: return@mapNotNull null
            when (status) {
                'A' -> ChangedFile(parts.last().replace('\\', '/'), ChangeType.ADDED)
                'M' -> ChangedFile(parts.last().replace('\\', '/'), ChangeType.MODIFIED)
                'D' -> ChangedFile(parts.last().replace('\\', '/'), ChangeType.DELETED)
                'R' -> ChangedFile(parts.last().replace('\\', '/'), ChangeType.RENAMED, oldPath = parts[1].replace('\\', '/'))
                'C' -> ChangedFile(parts.last().replace('\\', '/'), ChangeType.COPIED, oldPath = parts[1].replace('\\', '/'))
                else -> null
            }
        }.toList()

    private fun runGit(root: File, args: List<String>): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val status = process.waitFor()
        require(status == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }

    private fun writeOutput(encoded: String) {
        if (output != null) {
            val file = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }.apply { parentFile?.mkdirs() }
            file.writeText(encoded)
            echo("Verification report: ${file.path}")
        } else {
            echo(encoded)
        }
    }
}
