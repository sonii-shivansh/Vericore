package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.intelligence.PRIntelligenceAnalyzer
import com.vericore.core.intelligence.PRIntelligenceResult
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class PRIntelligenceCommand : CliktCommand(
    name = "pr-intelligence"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Analyze a Git change set for impact, risk, architecture, and test signals"
    private val path by argument("path", help = "Local Git repository path")
    private val base by option("--base", help = "Base Git revision; pair with --head")
    private val head by option("--head", help = "Head Git revision; pair with --base")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save pr-intelligence JSON artifact")

    override fun run() {
        require((base == null) == (head == null)) { "--base and --head must be supplied together" }
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val changeSet = if (base != null && head != null) {
            GitChangeSetBuilder.fromRevisions(root.path, base!!, head!!)
        } else {
            GitChangeSetBuilder.fromWorkingTree(root.path)
        }
        val result: PRIntelligenceResult = runBlocking {
            PRIntelligenceAnalyzer.analyze(root.path, changeSet, config)
        }

        val defaultOutput = root.resolve("output/pr-intelligence.json")
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(result))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val noChanges = result.changeSummary.filesChanged == 0
        val status = when {
            noChanges -> CommandStatus.NO_CHANGES
            result.aggregateSeverity.name == "CRITICAL" || result.aggregateSeverity.name == "HIGH" -> CommandStatus.REVIEW_REQUIRED
            else -> CommandStatus.PASS
        }

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "pr-intelligence",
                status = status,
                summary = if (noChanges) "No changes detected in working tree or revision range"
                    else "Analyzed ${result.changeSummary.filesChanged} changed file(s): +${result.changeSummary.additions}/-${result.changeSummary.deletions}, severity ${result.aggregateSeverity}",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = result,
                findings = result.findings.map { finding ->
                    EnvelopeFinding(
                        level = finding.severity.name,
                        message = finding.reason,
                        ruleId = finding.ruleId,
                        path = finding.paths.firstOrNull()?.let { repositoryRelativePath(it, root) }
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("pr-intelligence", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        if (output != null) {
            val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
            dest.parentFile?.mkdirs()
            dest.writeText(JsonEnvelope.json.encodeToString(result))
        }

        echo(renderPRIntelligenceSummary(result, defaultOutput.path))
    }
}

internal fun renderPRIntelligenceSummary(
    result: PRIntelligenceResult,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "PR Intelligence: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Changed files: ${result.changeSummary.filesChanged}",
                "Lines: +${result.changeSummary.additions} / -${result.changeSummary.deletions}",
                "Impacted files: ${result.impactedFiles}",
                "Cross-package impacts: ${result.crossPackageImpacts}",
                "Test candidates: ${result.testCandidates.size}",
                "Aggregate severity: ${result.aggregateSeverity}"
            )
        )
        if (result.findings.isNotEmpty()) {
            add(
                HumanCommandOutput.section(
                    "Findings",
                    *result.findings.map { "[${it.severity}] ${it.ruleId}: ${it.reason}" }.toTypedArray()
                )
            )
        }
    }
    return HumanCommandOutput.render(title = "🧭 PR Intelligence", sections = sections)
}
