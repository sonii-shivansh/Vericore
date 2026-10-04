package com.vericore.mcp

import com.vericore.cli.CodeParallelParser
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ArchitectureContractFileResolver
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureContract
import com.vericore.core.intelligence.ArchitectureContractEngine
import com.vericore.core.intelligence.ArchitectureContractResult
import com.vericore.core.intelligence.ArchitectureDriftEngine
import com.vericore.core.intelligence.ArchitectureDriftResult
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.intelligence.EngineeringContextDiff
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.intelligence.EngineeringContextSnapshot
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.scanner.RepositoryScanner
import com.vericore.core.workflow.AgentChangeContract
import com.vericore.core.workflow.ChangeSafetyAnalyzer
import com.vericore.core.workflow.EngineeringPreparation
import com.vericore.core.workflow.EngineeringVerification
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Shared MCP gateway over deterministic engineering-context workflows. */
object EngineeringContextGateway {
    private val json = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = false }
    fun reality(repoPath: String): JsonObject { val root = repository(repoPath); val config = ConfigLoader.loadForRepository(root.path); return json.parseToJsonElement(EngineeringContextEngine.encode(EngineeringContextEngine.snapshot(root, RepositoryScanner(config)))).jsonObject }
    fun snapshot(repoPath: String): JsonObject = reality(repoPath)
    fun diff(before: JsonObject, after: JsonObject): JsonObject { val b = json.decodeFromJsonElement(EngineeringContextSnapshot.serializer(), before); val a = json.decodeFromJsonElement(EngineeringContextSnapshot.serializer(), after); return json.encodeToJsonElement(EngineeringContextDiff.serializer(), EngineeringContextEngine.diff(b, a)).jsonObject }
    fun architectureDrift(repoPath: String, baseline: JsonObject): JsonObject { val root = repository(repoPath); val baselineResult = json.decodeFromJsonElement(ArchitectureIntelligenceResult.serializer(), baseline); val config = ConfigLoader.loadForRepository(root.path); val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(RepositoryScanner(config).scan(root.path)) }; val graph = RobustDependencyGraph(); graph.build(parsed).getOrThrow(); graph.analyze().getOrThrow(); val drift: ArchitectureDriftResult = ArchitectureDriftEngine.compare(baselineResult, ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)); return json.encodeToJsonElement(ArchitectureDriftResult.serializer(), drift).jsonObject }
    fun architectureContract(repoPath: String, contract: JsonObject?): JsonObject { val root = repository(repoPath); val config = ConfigLoader.loadForRepository(root.path); val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(RepositoryScanner(config).scan(root.path)) }; val graph = RobustDependencyGraph(); graph.build(parsed).getOrThrow(); graph.analyze().getOrThrow(); val architecture = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture); val contractValue = contract?.let { json.decodeFromJsonElement(ArchitectureContract.serializer(), it) } ?: ArchitectureContractFileResolver.resolve(root).file.takeIf { it.exists() }?.let { json.decodeFromString<ArchitectureContract>(it.readText()) } ?: ArchitectureContract(); return json.encodeToJsonElement(ArchitectureContractResult.serializer(), ArchitectureContractEngine.evaluate(architecture, contractValue)).jsonObject }
    fun prepare(repoPath: String, changeSummary: String): JsonObject { val root = repository(repoPath); val result = runBlocking { EngineeringPreparation.prepare(root.path, changeSummary) }; val output = root.resolve("output").apply { mkdirs() }; val config = ConfigLoader.loadForRepository(root.path); val snapshot = EngineeringContextEngine.snapshot(root, RepositoryScanner(config)); output.resolve("engineering-context.json").writeText(EngineeringContextEngine.encode(snapshot)); output.resolve("engineering-preparation.json").writeText(json.encodeToString(com.vericore.core.workflow.EngineeringPreparationResult.serializer(), result)); output.resolve("engineering-plan.json").writeText(json.encodeToString(EngineeringPlan.serializer(), result.plan)); output.resolve("agent-change-contract.json").writeText(json.encodeToString(AgentChangeContract.serializer(), result.contract)); return json.encodeToJsonElement(com.vericore.core.workflow.EngineeringPreparationResult.serializer(), result).jsonObject }
    /** Returns the exact persisted contract created by prepare; it never creates or reconstructs one. */
    fun changeContract(repoPath: String): JsonObject {
        val root = repository(repoPath)
        val contractFile = root.resolve("output/agent-change-contract.json")
        require(contractFile.isFile) { "Immutable agent change contract not found: ${contractFile.path}. Run prepare first." }
        val contract = json.decodeFromString<AgentChangeContract>(contractFile.readText())
        return json.encodeToJsonElement(AgentChangeContract.serializer(), contract).jsonObject
    }
    fun evidence(repoPath: String, changeSummary: String): JsonObject { val result = runBlocking { EngineeringPreparation.prepare(repository(repoPath).path, changeSummary) }; return json.encodeToJsonElement(com.vericore.core.ai.GroundedEvidence.serializer(), result.evidence).jsonObject }
    fun changeSafety(repoPath: String, plan: JsonObject): JsonObject { val root = repository(repoPath); val engineeringPlan = json.decodeFromJsonElement(EngineeringPlan.serializer(), plan); val changeSet = GitChangeSetBuilder.fromWorkingTree(root.path); val result = ChangeSafetyAnalyzer.verify(changeSet.files, engineeringPlan.plannedPaths); return json.encodeToJsonElement(com.vericore.core.workflow.ChangeSafetyResult.serializer(), result).jsonObject }
    fun verify(repoPath: String, plan: JsonObject): JsonObject {
        val root = repository(repoPath)
        val engineeringPlan = json.decodeFromJsonElement(EngineeringPlan.serializer(), plan)
        val contractFile = root.resolve("output/agent-change-contract.json")
        require(contractFile.isFile) { "Immutable agent change contract is required; run prepare first." }
        val persistedContract = json.decodeFromString<AgentChangeContract>(contractFile.readText())
        val result = runBlocking { EngineeringVerification.verify(root.path, engineeringPlan, persistedContract) }
        return json.encodeToJsonElement(com.vericore.core.workflow.EngineeringVerificationResult.serializer(), result).jsonObject
    }
    private fun repository(path: String): File { require(!path.startsWith("http://", true) && !path.startsWith("https://", true)) { "Remote repositories are not supported" }; val root = File(path).canonicalFile; require(root.isDirectory) { "Repository path is not a directory: $path" }; return root }
}
