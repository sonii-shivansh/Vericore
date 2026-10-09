package com.vericore.cli

import com.vericore.core.ai.AICodeAnalyzer
import com.vericore.core.ai.GroundedAIResponse
import com.vericore.core.ai.GroundedAIService
import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.intelligence.PRIntelligenceAnalyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser

class ReviewCommand : CliktCommand(
    name = "review",
    help = "Review a Git change with deterministic impact, risk, architecture, and test signals"
) {
    private val path by option("--path", help = "Repository path").default(".")
    private val base by option("--base", help = "Base Git revision; pair with --head")
    private val head by option("--head", help = "Head Git revision; pair with --base")
    private val jsonOutput by option("--json", help = "Write machine-readable JSON to output/review.json").flag()
    private val aiReview by option("--ai", help = "Add a grounded AI review using deterministic evidence and the Git diff").flag()

    override fun run() {
        require((base == null) == (head == null)) { "--base and --head must be supplied together" }
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $path" }
        val config = ConfigLoader.loadForRepository(root.path)
        val changeSet = if (base != null) {
            GitChangeSetBuilder.fromRevisions(root.path, base!!, head!!)
        } else {
            GitChangeSetBuilder.fromWorkingTree(root.path)
        }
        val result = runBlocking { PRIntelligenceAnalyzer.analyze(root.path, changeSet, config) }
        val ai = if (aiReview) runGroundedAiReview(root, config, result, base, head) else null
        val reviewOutput = ReviewOutput(result, ai)
        val json = Json { prettyPrint = true; encodeDefaults = true }

        if (jsonOutput) {
            val output = root.resolve("output/review.json")
            output.parentFile.mkdirs()
            val details = json.encodeToString(reviewOutput)
            output.writeText(details)
            echo(productJson(ProductCommandResult(
                command = "review",
                status = "COMPLETED",
                repository = root.path,
                findings = result.findings.map { finding ->
                    ProductFinding(finding.severity.toString(), buildString {
                        append(finding.reason)
                        if (finding.paths.isNotEmpty()) append(" (files: ${finding.paths.joinToString(", ")})")
                    })
                },
                artifacts = listOf(ProductArtifact("review", output.path)),
                nextStep = "vericore verify --path ${root.path} after prepare",
                details = json.parseToJsonElement(details)
            )))
            return
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
            if (finding.paths.isNotEmpty()) echo("      Files: ${finding.paths.take(5).joinToString(", ")}")
        }

        if (ai != null) {
            echo("🤖 Grounded AI Review")
            echo("├─ Confidence: ${String.format("%.2f", ai.confidence)}")
            echo("├─ Grounding: ${String.format("%.2f", ai.groundingScore)}")
            echo("└─ Evidence citations: ${ai.citedEvidenceIds.size}")
            echo("   ${ai.answer.replace(System.lineSeparator(), " ")}")
            if (ai.limitations.isNotEmpty()) echo("   ⚠️ ${ai.limitations.joinToString(" | ")}")
        }

        echo("Next: use `vericore verify` after prepare when a change contract exists.")
    }

    private fun runGroundedAiReview(
        root: File,
        config: com.vericore.core.config.VericoreConfig,
        deterministic: com.vericore.core.intelligence.PRIntelligenceResult,
        base: String?,
        head: String?
    ): GroundedAIResponse {
        require(config.ai.enabled) { "AI review is disabled in .vericore.json; enable AI before using --ai" }
        require(config.ai.apiKey.isNotBlank()) { "AI review requires a configured API key in .vericore.json" }

        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        require(snapshotFile.isFile) {
            "Grounded AI review requires output/analysis-snapshot.json. Run `vericore analyze` first."
        }

        val json = Json { ignoreUnknownKeys = true }
        val snapshot = json.decodeFromString<com.vericore.core.intelligence.AnalysisSnapshot>(snapshotFile.readText())
        val currentCommit = Git.open(root).use { it.repository.resolve("HEAD")?.name }
        require(snapshot.repository.repositoryCommit == currentCommit) {
            "Grounded AI review requires a current analysis snapshot. Run `vericore analyze` again."
        }

        if (head != null) {
            val headCommit = Git.open(root).use { git -> git.repository.resolve(head)?.name }
            require(headCommit != null && headCommit == currentCommit) {
                "Grounded AI review requires --head to point at the currently checked-out commit."
            }
        }

        val diff = buildGitDiff(root, base, head)
        val deterministicFacts = buildString {
            appendLine("DETERMINISTIC REVIEW RESULT")
            appendLine("aggregateSeverity=${deterministic.aggregateSeverity}")
            appendLine("changedFiles=${deterministic.changeSummary.filesChanged}")
            appendLine("additions=${deterministic.changeSummary.additions}")
            appendLine("deletions=${deterministic.changeSummary.deletions}")
            appendLine("impactedFiles=${deterministic.impactedFiles}")
            appendLine("impactedPackages=${deterministic.impactedPackages}")
            appendLine("crossPackageImpacts=${deterministic.crossPackageImpacts}")
            appendLine("testCandidates=${deterministic.testCandidates.joinToString(", ")}")
            deterministic.findings.forEach { finding ->
                appendLine("[${finding.ruleId}] severity=${finding.severity}; paths=${finding.paths.joinToString(", ")}; reason=${finding.reason}")
            }
        }

        val question = """
Review this Git change using ONLY the supplied deterministic review, Git diff, and grounded repository evidence.

Rules:
- Treat deterministic evidence as repository facts.
- Distinguish facts from inference and recommendations.
- Do not invent files, APIs, dependencies, tests, vulnerabilities, or architecture facts.
- Cite repository-specific claims with the supplied evidence IDs such as [repo.metrics] or [hotspot.1].
- If evidence is insufficient, say so explicitly.
- Focus on correctness, regression risk, security, breaking changes, test gaps, and actionable next steps.

$deterministicFacts

GIT DIFF:
${diff.take(12000)}${if (diff.length > 12000) "\n...[diff truncated]" else ""}
""".trimIndent()

        return runBlocking {
            GroundedAIService(
                AICodeAnalyzer(config.ai.apiKey, config.ai.model, config.ai.provider)
            ).ask(question, snapshot)
        }
    }

    private fun buildGitDiff(root: File, base: String?, head: String?): String {
        val output = ByteArrayOutputStream()
        Git.open(root).use { git ->
            val command = git.diff().setOutputStream(output)
            if (base != null && head != null) {
                command.setOldTree(treeParser(git.repository, base))
                command.setNewTree(treeParser(git.repository, head))
            }
            command.call()
        }
        return output.toString(Charsets.UTF_8)
    }

    private fun treeParser(repository: Repository, revision: String): CanonicalTreeParser {
        val commitId = repository.resolve(revision) ?: error("Unknown Git revision: $revision")
        RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(commitId)
            val parser = CanonicalTreeParser()
            repository.newObjectReader().use { reader -> parser.reset(reader, commit.tree.id) }
            return parser
        }
    }
}

@Serializable
private data class ReviewOutput(
    val deterministic: com.vericore.core.intelligence.PRIntelligenceResult,
    val ai: GroundedAIResponse? = null
)
