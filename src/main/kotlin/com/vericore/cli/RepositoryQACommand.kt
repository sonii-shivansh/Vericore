package com.vericore.cli

import com.vericore.core.ai.DependencyPaths
import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.ai.GroundedEvidenceBuilder
import com.vericore.core.cache.CacheManager
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.EngineeringContextSnapshot
import com.vericore.core.parser.ParsedFile
import com.vericore.core.qa.RepositoryEvidenceRetriever
import com.vericore.core.qa.RepositoryQuestionClassifier
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git

class RepositoryQACommand : CliktCommand(name = "repo-qa", help = "Retrieve grounded repository evidence for a developer question") {
    private val question by argument("question", help = "Question about the repository")
    private val path by option("--path", help = "Repository path").default(".")
    private val maxResults by option("--max-results", help = "Maximum evidence items").int().default(8)
    private val evidenceOutput by option(
        "--evidence-output",
        help = "Optional path for the reusable GroundedEvidence JSON artifact used by the plan command"
    )

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        require(maxResults in 1..32) { "--max-results must be between 1 and 32" }

        val parsedQuestion = RepositoryQuestionClassifier.classify(question)
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
        val cachedEvidence = loadReusableGroundedEvidence(root, json)
        if (cachedEvidence != null) {
            val result = RepositoryEvidenceRetriever().retrieve(parsedQuestion, cachedEvidence, maxResults)
            if (evidenceOutput != null) {
                val output = File(evidenceOutput!!)
                output.parentFile?.mkdirs()
                output.writeText(json.encodeToString(cachedEvidence))
            }
            echo(json.encodeToString(result))
            return
        }

        val files = RepositoryScanner().scan(root.path)
        val parsedFiles: List<ParsedFile> = runBlocking {
            CodeParallelParser(CacheManager()).parseFiles(files)
        }
        val parseFailures = files.size - parsedFiles.size
        val enriched = try {
            OptimizedGitAnalyzer().analyze(root.path, parsedFiles)
        } catch (_: Exception) {
            parsedFiles
        }
        val graph = RobustDependencyGraph()
        require(graph.build(enriched).isSuccess) { "Failed to build dependency graph" }
        require(graph.analyze().isSuccess) { "Failed to analyze dependency graph" }

        val snapshot = AnalysisSnapshotBuilder.build(
            repositoryPath = root.path,
            parsedFiles = enriched,
            graph = graph.graph,
            pageRankScores = graph.pageRankScores,
            hasCycles = graph.hasCycles,
            parseFailures = parseFailures,
            repositoryState = loadReusableRepositoryState(root, json)
        )
        val dependencyPaths = enriched.associate { parsed ->
            val sourcePath = parsed.file.absolutePath
            val dependents = if (graph.graph.containsVertex(sourcePath)) {
                graph.graph.incomingEdgesOf(sourcePath)
                    .map { graph.graph.getEdgeSource(it) }
                    .map { repositoryRelativePath(root, it) }
                    .sorted()
            } else emptyList()
            val dependencies = if (graph.graph.containsVertex(sourcePath)) {
                graph.graph.outgoingEdgesOf(sourcePath)
                    .map { graph.graph.getEdgeTarget(it) }
                    .map { repositoryRelativePath(root, it) }
                    .sorted()
            } else emptyList()
            sourcePath to DependencyPaths(dependents = dependents, dependencies = dependencies)
        }
        val grounded = GroundedEvidenceBuilder.fromSnapshot(snapshot, dependencyPaths = dependencyPaths)
        val result = RepositoryEvidenceRetriever().retrieve(parsedQuestion, grounded, maxResults)

        if (evidenceOutput != null) {
            val output = File(evidenceOutput!!)
            output.parentFile?.mkdirs()
            output.writeText(json.encodeToString(grounded))
        }
        echo(json.encodeToString(result))
    }

    private fun loadReusableGroundedEvidence(root: File, json: Json): GroundedEvidence? {
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        val evidenceFile = root.resolve("output/grounded-evidence.json")
        if (!snapshotFile.isFile || !evidenceFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
            val currentState = Git.open(root).use { git ->
                val commit = git.repository.resolve("HEAD")?.name
                val status = git.status().call()
                val changedPaths = status.modified + status.changed + status.added + status.untracked + status.removed + status.missing
                commit to changedPaths
            }
            require(currentState.second.all { it.startsWith("output/") || it == "output" }) {
                "Repository has source changes outside generated output"
            }
            require(snapshot.repository.repositoryCommit == currentState.first) { "Cached analysis snapshot is stale" }
            json.decodeFromString<GroundedEvidence>(evidenceFile.readText())
        }.getOrNull()
    }

    private fun loadReusableRepositoryState(root: File, json: Json): EngineeringContextSnapshot? {
        val contextFile = root.resolve("output/engineering-context.json")
        if (!contextFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<EngineeringContextSnapshot>(contextFile.readText())
            val currentCommit = Git.open(root).use { git -> git.repository.resolve("HEAD")?.name }
            if (snapshot.repositoryCommit != null && snapshot.repositoryCommit == currentCommit) snapshot else null
        }.getOrNull()
    }

    private fun repositoryRelativePath(root: File, file: String): String =
        root.toPath().relativize(File(file).canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
}
