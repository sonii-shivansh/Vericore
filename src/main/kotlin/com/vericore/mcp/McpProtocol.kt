package com.vericore.mcp

import com.vericore.core.Version
import com.vericore.core.ai.AICodeAnalyzer
import com.vericore.core.ai.GroundedAIResponse
import com.vericore.core.ai.GroundedAIService
import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ChangeImpactEngine
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.intelligence.PRIntelligenceAnalyzer
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.RecommendationEngine
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.server.AnalysisLogic
import com.vericore.server.sanitizePath
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayOutputStream
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser

private val json = Json { encodeDefaults = true; explicitNulls = false }
object McpProtocol {
    private const val PROTOCOL_VERSION = "2025-11-25"
    fun handle(request: JsonObject): JsonObject { val id = request["id"]; val method = request["method"]?.jsonPrimitive?.content ?: return errorResponse(id, -32600, "Invalid Request"); return when (method) { "initialize" -> initialize(id); "notifications/initialized" -> emptyResponse(); "ping" -> resultResponse(id, buildJsonObject {}); "tools/list" -> resultResponse(id, buildJsonObject { put("tools", toolDefinitions()) }); "tools/call" -> callTool(id, request["params"]?.jsonObject ?: buildJsonObject {}); else -> errorResponse(id, -32601, "Method not found: $method") } }
    fun runStdio(input: BufferedReader = BufferedReader(InputStreamReader(System.`in`))) { input.forEachLine { line -> if (line.isBlank()) return@forEachLine; val request = runCatching { json.parseToJsonElement(line).jsonObject }.getOrElse { println(json.encodeToString(JsonObject.serializer(), errorResponse(null, -32700, "Parse error"))); return@forEachLine }; val response = handle(request); if (response.isNotEmpty()) { println(json.encodeToString(JsonObject.serializer(), response)); System.out.flush() } } }
    private fun initialize(id: JsonElement?): JsonObject = resultResponse(id, buildJsonObject { put("protocolVersion", JsonPrimitive(PROTOCOL_VERSION)); put("capabilities", buildJsonObject { put("tools", buildJsonObject { put("listChanged", JsonPrimitive(false)) }) }); put("serverInfo", buildJsonObject { put("name", JsonPrimitive("Vericore")); put("version", JsonPrimitive(Version.current)) }); put("instructions", JsonPrimitive("Vericore provides deterministic, evidence-backed engineering intelligence. Prefer these tools before modifying a repository.")) })
    private fun callTool(id: JsonElement?, params: JsonObject): JsonObject { val name = params["name"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "Missing tool name"); val canonicalName = if (name.startsWith("codecontext_")) "vericore_" + name.removePrefix("codecontext_") else name; if (name.startsWith("codecontext_")) System.err.println("⚠️ Deprecated MCP tool '$name'; migrate to '$canonicalName'.") ; val args = params["arguments"]?.jsonObject ?: buildJsonObject {}; return try { resultResponse(id, when (canonicalName) { "vericore_analyze_repository" -> analyzeRepository(args); "vericore_impact_analysis" -> impactAnalysis(args); "vericore_architecture_analysis" -> architectureAnalysis(args); "vericore_pr_intelligence" -> prIntelligence(args); "vericore_review" -> review(args); "vericore_recommendations" -> recommendations(args); "vericore_get_engineering_reality" -> textResult(EngineeringContextGateway.reality(requireRepoPath(args))); "vericore_get_context_snapshot" -> textResult(EngineeringContextGateway.snapshot(requireRepoPath(args))); "vericore_get_context_diff" -> contextDiff(args); "vericore_get_architecture_drift" -> architectureDrift(args); "vericore_get_architecture_contract" -> architectureContract(args); "vericore_prepare_change" -> textResult(EngineeringContextGateway.prepare(requireRepoPath(args), requiredString(args, "changeSummary"), args["plannedPaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())); "vericore_get_change_contract" -> textResult(EngineeringContextGateway.changeContract(requireRepoPath(args))); "vericore_get_evidence" -> textResult(EngineeringContextGateway.evidence(requireRepoPath(args), args["changeSummary"]?.jsonPrimitive?.content ?: "Repository understanding")); "vericore_change_safety" -> textResult(EngineeringContextGateway.changeSafety(requireRepoPath(args), args["plan"]?.jsonObject ?: error("plan is required"))); "vericore_verify_change" -> textResult(EngineeringContextGateway.verify(requireRepoPath(args), args["plan"]?.jsonObject ?: error("plan is required"))); else -> return errorResponse(id, -32602, "Unknown tool: $name") }) } catch (e: IllegalArgumentException) { errorResponse(id, -32602, e.message ?: "Invalid tool arguments") } catch (e: Exception) { System.err.println("MCP tool '$name' failed: ${e::class.simpleName}"); errorResponse(id, -32603, "Tool execution failed") } }
    private fun recommendations(args: JsonObject): JsonObject { val path = safeRepoPath(args); val snapshotFile = File(path).resolve("output/analysis-snapshot.json"); require(snapshotFile.isFile) { "Recommendations require output/analysis-snapshot.json. Run `vericore analyze` first." }; val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<AnalysisSnapshot>(snapshotFile.readText()); val result = RecommendationEngine.generate(snapshot); return textResult(json.encodeToString(RecommendationListOutput.serializer(), RecommendationListOutput(result))) }
    private fun contextDiff(args: JsonObject): JsonObject { val before = args["before"]?.jsonObject ?: error("before snapshot is required"); val after = args["after"]?.jsonObject ?: error("after snapshot is required"); return textResult(EngineeringContextGateway.diff(before, after)) }
    private fun architectureDrift(args: JsonObject): JsonObject { val baseline = args["baseline"]?.jsonObject ?: error("baseline architecture snapshot is required"); return textResult(EngineeringContextGateway.architectureDrift(requireRepoPath(args), baseline)) }
    private fun architectureContract(args: JsonObject): JsonObject = textResult(EngineeringContextGateway.architectureContract(requireRepoPath(args), args["contract"]?.jsonObject))
    private fun analyzeRepository(args: JsonObject): JsonObject { val path = safeRepoPath(args); val config = ConfigLoader.loadForRepository(path); val result = runBlocking { AnalysisLogic.analyze(path, config) }; val graph = result.first; val parsedFiles = result.second; val hotspots = graph.getTopHotspots(10).map { (file, score) -> buildJsonObject { put("file", JsonPrimitive(file)); put("score", JsonPrimitive(score)) } }; val payload = buildJsonObject { put("schemaVersion", JsonPrimitive("1.0")); put("repository", JsonPrimitive(path)); put("fileCount", JsonPrimitive(parsedFiles.size)); put("nodeCount", JsonPrimitive(graph.graph.vertexSet().size)); put("edgeCount", JsonPrimitive(graph.graph.edgeSet().size)); put("hotspots", JsonArray(hotspots)) }; return textResult(json.encodeToString(JsonObject.serializer(), payload)) }
    private fun impactAnalysis(args: JsonObject): JsonObject { val path = safeRepoPath(args); val changedPaths = args["changedPaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(); require(changedPaths.isNotEmpty()) { "changedPaths must contain at least one repository-relative path" }; require(changedPaths.size <= 100) { "changedPaths may contain at most 100 paths" }; val config = ConfigLoader.loadForRepository(path); val (graph, parsedFiles, _) = runBlocking { AnalysisLogic.analyze(path, config) }; val enrichedFiles = OptimizedGitAnalyzer().analyze(path, parsedFiles); val pathLookup = enrichedFiles.associateBy { it.file.absolutePath.replace('\\', '/') }; val changedAbsolute = changedPaths.map { java.io.File(path, it).absolutePath.replace('\\', '/') }; val result = ChangeImpactEngine.analyze(graph.graph, changedAbsolute, graph.pageRankScores, pathLookup.mapValues { it.value.gitMetadata.changeFrequency }, pathLookup.mapValues { it.value.packageName }); return textResult(json.encodeToString(com.vericore.core.intelligence.ChangeImpactResult.serializer(), result)) }
    private fun architectureAnalysis(args: JsonObject): JsonObject { val path = safeRepoPath(args); val config = ConfigLoader.loadForRepository(path); val (graph, _, _) = runBlocking { AnalysisLogic.analyze(path, config) }; val result = ArchitectureIntelligenceEngine.analyze(graph.graph, java.io.File(path), config.architecture); return textResult(json.encodeToString(com.vericore.core.intelligence.ArchitectureIntelligenceResult.serializer(), result)) }
    private fun review(args: JsonObject): JsonObject {
        val path = safeRepoPath(args)
        val base = args["baseRevision"]?.jsonPrimitive?.content
        val head = args["headRevision"]?.jsonPrimitive?.content
        val includeAi = args["includeAi"]?.jsonPrimitive?.booleanOrNull ?: false
        require((base == null) == (head == null)) { "baseRevision and headRevision must be supplied together" }
        if (base != null) require(base.length <= 256 && head!!.length <= 256) { "Git revisions are too long" }
        val config = ConfigLoader.loadForRepository(path)
        val changeSet = if (base == null) GitChangeSetBuilder.fromWorkingTree(path) else GitChangeSetBuilder.fromRevisions(path, base, head!!)
        val result = runBlocking { PRIntelligenceAnalyzer.analyze(path, changeSet, config) }
        val ai = if (includeAi) groundedAiReview(File(path), config, result, base, head) else null
        return textResult(json.encodeToString(McpReviewOutput.serializer(), McpReviewOutput(result, ai)))
    }

    private fun groundedAiReview(
        root: File,
        config: com.vericore.core.config.VericoreConfig,
        deterministic: com.vericore.core.intelligence.PRIntelligenceResult,
        base: String?,
        head: String?
    ): GroundedAIResponse {
        require(config.ai.enabled) { "AI review is disabled in .vericore.json; enable AI before using includeAi" }
        require(config.ai.apiKey.isNotBlank()) { "AI review requires a configured API key in .vericore.json" }
        val snapshotFile = root.resolve("output/analysis-snapshot.json")
        require(snapshotFile.isFile) { "Grounded AI review requires output/analysis-snapshot.json. Run `vericore analyze` first." }
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<com.vericore.core.intelligence.AnalysisSnapshot>(snapshotFile.readText())
        val currentCommit = Git.open(root).use { it.repository.resolve("HEAD")?.name }
        require(snapshot.repository.repositoryCommit == currentCommit) { "Grounded AI review requires a current analysis snapshot. Run `vericore analyze` again." }
        if (head != null) {
            val headCommit = Git.open(root).use { git -> git.repository.resolve(head)?.name }
            require(headCommit != null && headCommit == currentCommit) { "Grounded AI review requires headRevision to point at the checked-out commit." }
        }
        val diff = buildGitDiff(root, base, head)
        val facts = buildString {
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
- Cite repository-specific claims with supplied evidence IDs.
- If evidence is insufficient, say so explicitly.
- Focus on correctness, regression risk, security, breaking changes, test gaps, and actionable next steps.

$facts

GIT DIFF:
${diff.take(12000)}${if (diff.length > 12000) "\n...[diff truncated]" else ""}
""".trimIndent()
        return runBlocking {
            GroundedAIService(AICodeAnalyzer(config.ai.apiKey, config.ai.model, config.ai.provider)).ask(question, snapshot)
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

    private fun prIntelligence(args: JsonObject): JsonObject { val path = safeRepoPath(args); val base = args["baseRevision"]?.jsonPrimitive?.content; val head = args["headRevision"]?.jsonPrimitive?.content; require((base == null) == (head == null)) { "baseRevision and headRevision must be supplied together" }; if (base != null) require(base.length <= 256 && head!!.length <= 256) { "Git revisions are too long" }; val changeSet = if (base == null) GitChangeSetBuilder.fromWorkingTree(path) else GitChangeSetBuilder.fromRevisions(path, base, head!!); val result = runBlocking { PRIntelligenceAnalyzer.analyze(path, changeSet, ConfigLoader.loadForRepository(path)) }; return textResult(json.encodeToString(com.vericore.core.intelligence.PRIntelligenceResult.serializer(), result)) }
    private fun safeRepoPath(args: JsonObject): String { val input = args["repoPath"]?.jsonPrimitive?.content ?: error("repoPath is required"); require(!input.startsWith("http://", true) && !input.startsWith("https://", true)) { "Remote repositories are not supported" }; return sanitizePath(input) ?: error("Invalid or unsafe repository path") }
    private fun requireRepoPath(args: JsonObject): String = safeRepoPath(args)
    private fun requiredString(args: JsonObject, key: String): String = args[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("$key is required")
    private fun toolDefinitions(): JsonArray = buildJsonArray { add(tool("vericore_analyze_repository", "Analyze a repository and return deterministic structure, graph, and hotspot evidence.", repositorySchema())); add(tool("vericore_impact_analysis", "Calculate deterministic dependency impact for changed repository-relative paths.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")); add(JsonPrimitive("changedPaths")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("changedPaths", buildJsonObject { put("type", JsonPrimitive("array")); put("items", stringProperty("Repository-relative changed path")); put("maxItems", JsonPrimitive(100)) }) }) })); add(tool("vericore_architecture_analysis", "Analyze architecture boundaries, dependencies, and architectural signals.", repositorySchema())); add(tool("vericore_pr_intelligence", "Analyze working-tree or revision-to-revision changes and return PR intelligence.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("baseRevision", stringProperty("Optional Git base revision")); put("headRevision", stringProperty("Optional Git head revision")) }) })); add(tool("vericore_review", "Review a change with deterministic impact, risk, architecture, and test signals, optionally followed by grounded AI interpretation.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("baseRevision", stringProperty("Optional Git base revision")); put("headRevision", stringProperty("Optional Git head revision")); put("includeAi", buildJsonObject { put("type", JsonPrimitive("boolean")); put("description", JsonPrimitive("Optional grounded AI review; requires enabled AI configuration and a current analysis snapshot")) }) }) })); add(tool("vericore_recommendations", "Return prioritized deterministic engineering recommendations from the repository analysis snapshot.", repositorySchema())); add(tool("vericore_get_engineering_reality", "Return the deterministic repository-state snapshot an agent should trust before editing.", repositorySchema())); add(tool("vericore_get_context_snapshot", "Return a content-addressed engineering context snapshot.", repositorySchema())); add(tool("vericore_get_context_diff", "Compare two engineering context snapshots without rescanning the repository.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("before")); add(JsonPrimitive("after")) }); put("properties", buildJsonObject { put("before", buildJsonObject { put("type", JsonPrimitive("object")) }); put("after", buildJsonObject { put("type", JsonPrimitive("object")) }) }) })); add(tool("vericore_get_architecture_drift", "Compare a baseline architecture intelligence snapshot with the current repository architecture.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")); add(JsonPrimitive("baseline")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("baseline", buildJsonObject { put("type", JsonPrimitive("object")); put("description", JsonPrimitive("ArchitectureIntelligenceResult JSON")) }) }) })); add(tool("vericore_get_architecture_contract", "Evaluate the current architecture against a deterministic architecture contract.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("contract", buildJsonObject { put("type", JsonPrimitive("object")); put("description", JsonPrimitive("Optional ArchitectureContract JSON; repository contract file is used when omitted")) }) }) })); add(tool("vericore_prepare_change", "Build grounded evidence, a deterministic engineering plan, and an immutable change contract before a change.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")); add(JsonPrimitive("changeSummary")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("changeSummary", stringProperty("Requested engineering change")); put("plannedPaths", buildJsonObject { put("type", JsonPrimitive("array")); put("items", stringProperty("Repository-relative path the agent expects to modify")); put("maxItems", JsonPrimitive(100)) }) }) })); add(tool("vericore_get_change_contract", "Return the exact persisted immutable change contract created by prepare; this tool never creates a new contract.", repositorySchema())); add(tool("vericore_get_evidence", "Return grounded repository evidence suitable for an agent context window.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("changeSummary", stringProperty("Optional evidence focus")) }) })); add(tool("vericore_change_safety", "Evaluate whether current working-tree changes remain within a grounded engineering plan.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")); add(JsonPrimitive("plan")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("plan", buildJsonObject { put("type", JsonPrimitive("object")); put("description", JsonPrimitive("EngineeringPlan JSON returned by vericore_prepare_change")) }) }) })); add(tool("vericore_verify_change", "Verify a working-tree change against the persisted immutable agent change contract and its bound engineering plan.", buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")); add(JsonPrimitive("plan")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")); put("plan", buildJsonObject { put("type", JsonPrimitive("object")); put("description", JsonPrimitive("EngineeringPlan JSON returned by vericore_prepare_change")) }) }) })) }
    private fun repositorySchema(): JsonObject = buildJsonObject { put("type", JsonPrimitive("object")); put("required", buildJsonArray { add(JsonPrimitive("repoPath")) }); put("properties", buildJsonObject { put("repoPath", stringProperty("Absolute repository path")) }) }
    private fun stringProperty(description: String): JsonObject = buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive(description)) }
    private fun tool(name: String, description: String, inputSchema: JsonObject): JsonObject = buildJsonObject { put("name", JsonPrimitive(name)); put("description", JsonPrimitive(description)); put("inputSchema", inputSchema) }
    private fun textResult(payload: JsonObject): JsonObject = buildJsonObject { put("content", buildJsonArray { add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(json.encodeToString(JsonObject.serializer(), payload))) }) }); put("isError", JsonPrimitive(false)) }
    private fun textResult(text: String): JsonObject = buildJsonObject { put("content", buildJsonArray { add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) }) }); put("isError", JsonPrimitive(false)) }
    private fun resultResponse(id: JsonElement?, result: JsonObject): JsonObject = buildJsonObject { put("jsonrpc", JsonPrimitive("2.0")); if (id != null) put("id", id); put("result", result) }
    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject { put("jsonrpc", JsonPrimitive("2.0")); if (id != null) put("id", id); put("error", buildJsonObject { put("code", JsonPrimitive(code)); put("message", JsonPrimitive(message)) }) }
    @kotlinx.serialization.Serializable
    private data class McpReviewOutput(
        val deterministic: com.vericore.core.intelligence.PRIntelligenceResult,
        val ai: GroundedAIResponse? = null
    )

    @kotlinx.serialization.Serializable
    private data class RecommendationListOutput(
        val recommendations: List<com.vericore.core.intelligence.EngineeringRecommendation>
    )

    private fun emptyResponse(): JsonObject = buildJsonObject {}
}
