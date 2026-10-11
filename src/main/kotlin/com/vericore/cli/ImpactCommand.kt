package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ChangeImpactEngine
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ImpactCommand : CliktCommand(
    name = "impact",
    help = "Analyze the dependency impact of changed files"
) {
    private val path by argument("path", help = "Repository path")
    private val changed by argument("changed", help = "Changed file path(s) relative to the repository").multiple(required = true)
    private val jsonOutput by option("--json", help = "Write machine-readable impact JSON").flag()

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

        if (jsonOutput) {
            val output = root.resolve("output/change-impact.json")
            output.parentFile.mkdirs()
            output.writeText(Json { prettyPrint = true }.encodeToString(result))
            echo("🧭 Impact report: ${output.absolutePath}")
        }

        echo(renderImpactSummary(result.summary))

        result.nodes.take(20).forEach { node ->
            val displayPath = node.path.replace('\\', '/').removePrefix("${root.path.replace('\\', '/')}/")
            echo("   ${node.relationship}: $displayPath (depth=${node.depth}, score=${String.format("%.1f", node.score)})")
        }
    }
}


/** Formats only the human summary; JSON serialization and impact calculations remain unchanged. */
internal fun renderImpactSummary(summary: com.vericore.core.intelligence.ImpactSummary): String =
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
