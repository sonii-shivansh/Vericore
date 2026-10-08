package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.intelligence.PRIntelligenceAnalyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReviewCommand : CliktCommand(name = "review", help = "Review a Git change with deterministic impact, risk, architecture, and test signals") {
    private val path by option("--path", help = "Repository path").default(".")
    private val base by option("--base", help = "Base Git revision; pair with --head")
    private val head by option("--head", help = "Head Git revision; pair with --base")
    private val jsonOutput by option("--json", help = "Write machine-readable JSON to output/review.json").flag()

    override fun run() {
        require((base == null) == (head == null)) { "--base and --head must be supplied together" }
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val changeSet = if (base != null) GitChangeSetBuilder.fromRevisions(root.path, base!!, head!!) else GitChangeSetBuilder.fromWorkingTree(root.path)
        val result = runBlocking { PRIntelligenceAnalyzer.analyze(root.path, changeSet, config) }
        if (jsonOutput) {
            val output = root.resolve("output/review.json")
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(result))
            echo("Review JSON: ${output.absolutePath}")
        }
        echo("🔎 Vericore Review")
        echo("├─ Changed files: ${result.changeSummary.filesChanged}")
        echo("├─ Lines: +${result.changeSummary.additions} / -${result.changeSummary.deletions}")
        echo("├─ Impacted files: ${result.impactedFiles}")
        echo("├─ Cross-package impacts: ${result.crossPackageImpacts}")
        echo("├─ Test candidates: ${result.testCandidates.size}")
        echo("└─ Risk: ${result.aggregateSeverity}")
        if (result.findings.isEmpty()) echo("   No deterministic review findings.")
        result.findings.forEach { finding ->
            echo("   [${finding.severity}] ${finding.ruleId}")
            echo("      ${finding.reason}")
            if (finding.affectedPaths.isNotEmpty()) echo("      Files: ${finding.affectedPaths.take(5).joinToString(", ")}")
        }
        echo("Next: use `vericore verify` after prepare when a change contract exists.")
    }
}