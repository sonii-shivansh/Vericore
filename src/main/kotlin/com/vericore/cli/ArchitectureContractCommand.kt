package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ArchitectureContractFileResolver
import com.vericore.core.config.ConfigLoader
import com.vericore.core.exceptions.ValidationException
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureContract
import com.vericore.core.intelligence.ArchitectureContractEngine
import com.vericore.core.intelligence.ArchitectureContractHistoryStore
import com.vericore.core.intelligence.ArchitectureContractResult
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArchitectureContractCommand : CliktCommand(
    name = "architecture-contract",
    help = "Evaluate repository architecture against a deterministic contract"
) {
    private val path by argument("path", help = "Repository path")
    private val contractPath by option("--contract", help = "Architecture contract JSON; defaults to .vericore-architecture-contract.json")
    private val jsonOutput by option("--json", help = "Write machine-readable contract result").flag()
    private val recordPath by option("--record", help = "Persist the deterministic contract decision history")

    override fun run() {
        val root = File(path).absoluteFile.normalize()
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) {
            "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}"
        }
        val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(files) }
        val graph = RobustDependencyGraph()
        graph.build(parsed).getOrThrow()
        graph.analyze().getOrThrow()
        val architecture = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)
        val json = Json { ignoreUnknownKeys = false; prettyPrint = true; encodeDefaults = true }
        val resolution = contractPath?.let { ArchitectureContractFileResolver.Resolution(File(it), usedLegacy = false) }
            ?: ArchitectureContractFileResolver.resolve(root)
        if (resolution.usedLegacy) {
            echo("⚠️ Using legacy CodeContext architecture contract. Migrate to .vericore-architecture-contract.json")
        }
        val contract = if (resolution.file.exists()) {
            json.decodeFromString<ArchitectureContract>(resolution.file.readText())
        } else {
            ArchitectureContract()
        }
        val result = ArchitectureContractEngine.evaluate(architecture, contract)
        val decision = ArchitectureContractHistoryStore.decision(root, contract, result)
        recordPath?.let { target ->
            val historyFile = File(target)
            ArchitectureContractHistoryStore.record(historyFile, decision)
            echo("🧾 Contract decision history: ${historyFile.absolutePath}")
        }
        if (jsonOutput) {
            val output = outputPath(root)
            output.parentFile.mkdirs()
            output.writeText(json.encodeToString<ArchitectureContractResult>(result))
        }
        echo(
            renderArchitectureContractSummary(
                result,
                findingsCount = architecture.findings.size,
                cyclesCount = architecture.cycles.size,
                decisionId = decision.decisionId,
                outputPath = if (jsonOutput) outputPath(root).absolutePath else null,
                recordPath = recordPath?.let { File(it).absolutePath }
            )
        )
        if (!result.passed) {
            throw ValidationException("Architecture contract failed")
        }
    }
}

internal fun renderArchitectureContractSummary(
    result: ArchitectureContractResult,
    findingsCount: Int,
    cyclesCount: Int,
    decisionId: String,
    outputPath: String? = null,
    recordPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Architecture contract: $it")) }
        recordPath?.let { add(HumanCommandOutput.section("History", "Contract decision history: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Passed: ${result.passed}",
                "Findings: $findingsCount",
                "Cycles: $cyclesCount",
                "Violations: ${result.violations.size}",
                "Contract fingerprint: $decisionId"
            )
        )
        result.violations.take(30).takeIf { it.isNotEmpty() }?.let { violations ->
            add(HumanCommandOutput.section("Violations", *violations.map { "${it.ruleId}: ${it.message}" }.toTypedArray()))
        }
    }
    return HumanCommandOutput.render(title = "🛡️ Architecture Contract", sections = sections)
}

private fun outputPath(root: File): File = root.resolve("output/architecture-contract.json")
