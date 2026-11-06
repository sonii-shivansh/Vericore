package com.vericore.cli

import com.vericore.core.ai.DependencyPaths
import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.ai.GroundedEvidenceBuilder
import com.vericore.core.cache.CacheManager
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.EngineeringContextSnapshot
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.parser.ParsedFile
import com.vericore.core.qa.RepositoryEvidenceRetriever
import com.vericore.core.qa.RepositoryQuestionClassifier
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git

class RepositoryQACommand : CliktCommand(name = "repo-qa") {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Retrieve grounded repository evidence for a developer question"
    private val question by argument("question", help = "Question about the repository")
    private val path by option("--path", help = "Repository path").default(".")
    private val maxResults by option("--max-results", help = "Maximum evidence items").int().default(8)
    private val evidenceOutput by option(
        "--evidence-output",
        help = "Optional path for the reusable GroundedEvidence JSON artifact used by the plan command"
    )
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save JSON output")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        require(maxResults in 1..32) { "--max-results must be between 1 and 32" }

        val parsedQuestion = RepositoryQuestionClassifier.classify(question)
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
        val cachedEvidence = loadReusableGroundedEvidence(root, json)
        if (cachedEvidence != null) {
            return emitResult(root, parsedQuestion, cachedEvidence, json)
        }

        // The live release gate already ran `analyze` immediately before `repo-qa`.
        // Reuse that immutable snapshot when HEAD is unchanged and only generated
        // output files are dirty. This avoids reparsing a 20k+ file repository.
        val reusableSnapshot = loadReusableAnalysisSnapshot(root, json)
        if (reusableSnapshot != null) {
            val grounded = GroundedEvidenceBuilder.fromSnapshot(reusableSnapshot)
            return emitResult(root, parsedQuestion, grounded, json)
        }

        val files = RepositoryScanner().scan(root.path)
        val parser = CodeParallelParser(CacheManager())
        val parsedFiles: List<ParsedFile> = runBlocking {
            parser.parseFiles(files)
        }
        val parseFailures = parser.lastWarningCount
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
        emitResult(root, parsedQuestion, grounded, json)
    }

    private fun emitResult(
        root: File,
        parsedQuestion: com.vericore.core.qa.RepositoryQuestion,
        grounded: GroundedEvidence,
        json: Json
    ) {
        val result = RepositoryEvidenceRetriever().retrieve(parsedQuestion, grounded, maxResults)
        if (evidenceOutput != null) {
            val evFile = File(evidenceOutput!!)
            evFile.parentFile?.mkdirs()
            evFile.writeText(json.encodeToString(grounded))
        }

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val envelope = CommandEnvelope(
            schemaVersion = "1.0",
            command = "repo-qa",
            status = if (result.insufficientEvidence) CommandStatus.NO_FINDINGS else CommandStatus.SUCCESS,
            summary = "Grounded evidence retrieval for '${parsedQuestion.question}'",
            repository = RepositoryInfo(name = root.name, path = root.path),
            data = result,
            findings = result.evidence.map { item ->
                EnvelopeFinding(
                    level = "INFO",
                    message = "${item.citation.detail} (relevance=${item.relevance})",
                    ruleId = item.citation.type,
                    path = item.citation.path?.let { repositoryRelativePath(it, root) }
                )
            },
            artifacts = listOfNotNull(
                evidenceOutput?.let { EnvelopeArtifact("grounded-evidence", it) }
            ),
            execution = ExecutionInfo(exitCode = 0)
        )

        if (output != null) {
            val outFile = File(output!!)
            outFile.parentFile?.mkdirs()
            outFile.writeText(JsonEnvelope.encode(envelope))
        }

        if (outFormat == OutputFormat.JSON) {
            echo(JsonEnvelope.encode(envelope))
        } else {
            echo(renderRepositoryQASummary(result, root, evidenceOutput))
        }
    }

    private fun loadReusableGroundedEvidence(root: File, json: Json): GroundedEvidence? {
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        val evidenceFile = root.resolve("output/grounded-evidence.json")
        if (!snapshotFile.isFile || !evidenceFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
            val currentState = currentRepositoryState(root)
            require(currentState.second.all { it.startsWith("output/") || it == "output" }) {
                "Repository has source changes outside generated output"
            }
            val currentContext = EngineeringContextEngine.snapshot(root, RepositoryScanner())
            require(snapshot.repository.path == root.path) { "Cached analysis snapshot belongs to a different repository" }
            require(snapshot.repository.repositoryCommit == currentState.first) { "Cached analysis snapshot is stale" }
            require(
                !snapshot.repository.repositoryStateDigest.isNullOrBlank() &&
                    snapshot.repository.repositoryStateDigest == currentContext.snapshotDigest
            ) { "Cached analysis snapshot source state is stale" }
            val evidence = json.decodeFromString<GroundedEvidence>(evidenceFile.readText())
            require(evidence.isBoundTo(snapshot, root.path)) { "Cached grounded evidence is stale or belongs to another snapshot" }
            evidence
        }.getOrNull()
    }

    private fun loadReusableAnalysisSnapshot(root: File, json: Json): AnalysisSnapshot? {
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        if (!snapshotFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<AnalysisSnapshot>(snapshotFile.readText())
            val currentState = currentRepositoryState(root)
            require(currentState.second.all { it.startsWith("output/") || it == "output" }) {
                "Repository has source changes outside generated output"
            }
            val currentContext = EngineeringContextEngine.snapshot(root, RepositoryScanner())
            require(snapshot.repository.path == root.path) { "Cached analysis snapshot belongs to a different repository" }
            require(snapshot.repository.repositoryCommit == currentState.first) { "Cached analysis snapshot is stale" }
            require(
                !snapshot.repository.repositoryStateDigest.isNullOrBlank() &&
                    snapshot.repository.repositoryStateDigest == currentContext.snapshotDigest
            ) { "Cached analysis snapshot source state is stale" }
            snapshot
        }.getOrNull()
    }

    private fun currentRepositoryState(root: File): Pair<String?, Set<String>> = Git.open(root).use { git ->
        val commit = git.repository.resolve("HEAD")?.name
        val status = git.status().call()
        val changedPaths = status.modified + status.changed + status.added + status.untracked + status.removed + status.missing
        commit to changedPaths
    }

    private fun loadReusableRepositoryState(root: File, json: Json): EngineeringContextSnapshot? {
        val contextFile = root.resolve("output/engineering-context.json")
        if (!contextFile.isFile) return null
        return runCatching {
            val snapshot = json.decodeFromString<EngineeringContextSnapshot>(contextFile.readText())
            val currentState = currentRepositoryState(root)
            require(currentState.second.all { it.startsWith("output/") || it == "output" }) {
                "Repository has source changes outside generated output"
            }
            val currentContext = EngineeringContextEngine.snapshot(root, RepositoryScanner())
            require(snapshot.repositoryCommit == currentState.first) { "Cached engineering context is stale" }
            require(snapshot.snapshotDigest == currentContext.snapshotDigest) { "Cached engineering context source state is stale" }
            snapshot
        }.getOrNull()
    }

    private fun repositoryRelativePath(root: File, file: String): String =
        root.toPath().relativize(File(file).canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
}

internal fun renderRepositoryQASummary(
    result: com.vericore.core.qa.RepositoryQAResult,
    root: File,
    evidencePath: String? = null
): String {
    val sections = buildList {
        evidencePath?.let { add(HumanCommandOutput.section("Artifact", "Grounded evidence: $it")) }
        add(
            HumanCommandOutput.section(
                "Question",
                "Query: \"${result.question.question}\"",
                "Intent: ${result.question.intent}",
                "Entity: ${result.question.entity ?: "none detected"}"
            )
        )
        if (result.insufficientEvidence || result.evidence.isEmpty()) {
            add(
                HumanCommandOutput.section(
                    "Evidence",
                    "No relevant citations found for this question."
                )
            )
        } else {
            add(
                HumanCommandOutput.section(
                    "Retrieved Evidence (${result.evidence.size} citations)",
                    *result.evidence.mapIndexed { index, item ->
                        val pathStr = item.citation.path?.let { " [${repositoryRelativePath(it, root)}]" } ?: ""
                        "${index + 1}. [score ${item.relevance}] ${item.citation.id}$pathStr — ${item.citation.detail}"
                    }.toTypedArray()
                )
            )
        }
    }
    return HumanCommandOutput.render(
        title = "🔎 Repository QA",
        status = if (result.insufficientEvidence) CommandStatus.NO_FINDINGS else CommandStatus.SUCCESS,
        sections = sections,
        nextStep = if (result.evidence.isNotEmpty()) "Run `vericore plan \"...\"` to prepare a change using this evidence." else null
    )
}
