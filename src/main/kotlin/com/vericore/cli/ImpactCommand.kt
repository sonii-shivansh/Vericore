package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ChangeImpactEngine
import com.vericore.core.intelligence.ChangeImpactResult
import com.vericore.core.intelligence.ImpactRelationship
import com.vericore.core.intelligence.ImpactSummary
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ImpactCommand : CliktCommand(
    name = "impact"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Analyze the dependency impact of changed files"
    private val path by argument("path", help = "Repository path")
    private val changed by argument("changed", help = "Changed file path(s) relative to the repository").multiple(required = true)
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable impact JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save impact JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val files = RepositoryScanner(config).scan(root.path)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }

        val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(files) }
        val enriched = OptimizedGitAnalyzer().analyze(root.path, parsed)
        val graph = RobustDependencyGraph()
        graph.build(enriched).getOrThrow()
        graph.analyze().getOrThrow()

        val pathLookup = enriched.associateBy { it.file.absolutePath.replace('\\', '/') }
        val changedAbsolute = changed.map { File(root, it).absoluteFile.normalize().path.replace('\\', '/') }
        val graphPaths = graph.graph.vertexSet().map { it.replace('\\', '/') }.toSet()
        val unresolved = changedAbsolute.filter { requested ->
            requested !in graphPaths && graphPaths.none { candidate -> candidate.endsWith("/${requested.trimStart('/')}") }
        }
        require(unresolved.isEmpty()) {
            "Changed file(s) were not found in the analyzed dependency graph: ${unresolved.joinToString()}. " +
                "Paths must point to files supported by repository analysis."
        }

        val packageByPath = pathLookup.mapValues { it.value.packageName }
        val churnByPath = pathLookup.mapValues { it.value.gitMetadata.changeFrequency }

        val result = ChangeImpactEngine.analyze(
            graph = graph.graph,
            changedPaths = changedAbsolute,
            pageRankScores = graph.pageRankScores,
            churnByPath = churnByPath,
            packageByPath = packageByPath
        )

        require(result.summary.changedFiles == changedAbsolute.distinct().size) {
            "Impact analysis could not resolve all requested changed files."
        }

        val defaultOutput = root.resolve("output/change-impact.json")
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(result))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val testCandidateNodes = result.nodes.filter { it.relationship == ImpactRelationship.TEST_CANDIDATE }

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "impact",
                status = CommandStatus.SUCCESS,
                summary = "Impact analysis for ${changedAbsolute.size} changed file(s): ${result.summary.impactedFiles} impacted files, ${result.summary.testCandidates} test candidates",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = result,
                findings = testCandidateNodes.map { node ->
                    EnvelopeFinding(
                        level = "INFO",
                        message = node.reasons.firstOrNull() ?: "Test candidate for changed dependencies",
                        ruleId = "TEST_CANDIDATE",
                        path = repositoryRelativePath(node.path, root)
                    )
                },
                recommendations = testCandidateNodes.map { node ->
                    EnvelopeRecommendation(
                        priority = "HIGH",
                        title = "Execute impact-detected test candidate",
                        action = "Run tests in ${repositoryRelativePath(node.path, root)} (${node.reasons.firstOrNull() ?: "test candidate"})",
                        evidencePaths = listOf(repositoryRelativePath(node.path, root))
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("impact-report", defaultOutput.path)
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

        echo(renderImpactSummary(result, root, defaultOutput.path))
    }
}

/** Formats the human summary with clear metrics, relative test candidates, and impacted components. */
internal fun renderImpactSummary(
    result: ChangeImpactResult,
    root: File,
    artifactPath: String? = null
): String {
    val summary = result.summary
    val testCandidates = result.nodes.filter { it.relationship == ImpactRelationship.TEST_CANDIDATE }
    val sections = buildList {
        artifactPath?.let { add(HumanCommandOutput.section("Artifact", "Impact report: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Changed files: ${summary.changedFiles}",
                "Impacted files: ${summary.impactedFiles}",
                "Impacted packages: ${summary.impactedPackages}",
                "Cross-package impacts: ${summary.crossPackageImpacts}",
                "Test candidates: ${summary.testCandidates}",
                "Maximum dependency depth: ${summary.maxDepth}"
            )
        )
        if (testCandidates.isNotEmpty()) {
            add(
                HumanCommandOutput.section(
                    "Test candidates (${testCandidates.size})",
                    *testCandidates.map {
                        val relPath = repositoryRelativePath(it.path, root)
                        val reason = it.reasons.firstOrNull() ?: "likely test coverage candidate"
                        "$relPath — $reason"
                    }.toTypedArray()
                )
            )
        }
        val topImpacted = result.nodes.take(20)
        if (topImpacted.isNotEmpty()) {
            add(
                HumanCommandOutput.section(
                    "Impacted components",
                    *topImpacted.map { node ->
                        val displayPath = repositoryRelativePath(node.path, root)
                        "${node.relationship}: $displayPath (depth=${node.depth}, score=${String.format("%.1f", node.score)})"
                    }.toTypedArray()
                )
            )
        }
    }
    return HumanCommandOutput.render(
        title = "🧭 Change Impact",
        status = CommandStatus.SUCCESS,
        sections = sections,
        nextStep = if (testCandidates.isNotEmpty()) "Run the recommended test candidates before verifying changes." else null
    )
}

internal fun renderImpactSummary(summary: ImpactSummary): String =
    HumanCommandOutput.render(
        title = "🧭 Change Impact",
        sections = listOf(
            HumanCommandOutput.section(
                "Summary",
                "Changed files: ${summary.changedFiles}",
                "Impacted files: ${summary.impactedFiles}",
                "Impacted packages: ${summary.impactedPackages}",
                "Cross-package impacts: ${summary.crossPackageImpacts}",
                "Test candidates: ${summary.testCandidates}",
                "Maximum dependency depth: ${summary.maxDepth}"
            )
        )
    )
