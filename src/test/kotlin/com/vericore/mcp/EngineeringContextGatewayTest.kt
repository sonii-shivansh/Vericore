package com.vericore.mcp

import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.scanner.RepositoryScanner
import com.vericore.core.config.VericoreConfig
import com.vericore.cli.CodeParallelParser
import com.vericore.core.cache.CacheManager
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.workflow.AgentChangeContract
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EngineeringContextGatewayTest {
    @Test
    fun `architecture drift gateway serializes a deterministic result`() {
        val root = Files.createTempDirectory("gateway-drift").toFile()
        try {
            root.resolve("src/App.kt").apply { parentFile.mkdirs(); writeText("class App") }
            val config = VericoreConfig(excludePaths = emptyList())
            val parsed = runBlocking { CodeParallelParser(CacheManager()).parseFiles(RepositoryScanner(config).scan(root.path)) }
            val graph = RobustDependencyGraph()
            graph.build(parsed).getOrThrow()
            graph.analyze().getOrThrow()
            val architecture = ArchitectureIntelligenceEngine.analyze(graph.graph, root, config.architecture)
            val baseline = Json { encodeDefaults = true }.encodeToJsonElement(ArchitectureIntelligenceResult.serializer(), architecture).jsonObject
            val result = EngineeringContextGateway.architectureDrift(root.path, baseline)
            assertNotNull(result["summary"] ?: result["changes"])
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `change safety never widens empty planned scope to affected components`() {
        val root = Files.createTempDirectory("gateway-safety-scope").toFile()
        try {
            root.resolve("src/App.kt").apply { parentFile.mkdirs(); writeText("class App") }
            root.resolve("src/Related.kt").writeText("class Related")
            org.eclipse.jgit.api.Git.init().setDirectory(root).call().use { git ->
                git.add().addFilepattern(".").call()
                git.commit().setMessage("baseline")
                    .setAuthor(org.eclipse.jgit.lib.PersonIdent("test", "test@example.com"))
                    .setCommitter(org.eclipse.jgit.lib.PersonIdent("test", "test@example.com"))
                    .call()
            }
            root.resolve("src/Related.kt").appendText("\nfun changed() = Unit\n")
            val plan = Json { encodeDefaults = true }.encodeToJsonElement(
                com.vericore.core.planner.EngineeringPlan.serializer(),
                com.vericore.core.planner.EngineeringPlan(
                    changeSummary = "test scope",
                    repository = root.canonicalPath,
                    affectedComponents = listOf("src/Related.kt"),
                    plannedPaths = emptyList(),
                    concerns = emptyList(),
                    riskLevel = com.vericore.core.planner.RiskLevel.LOW,
                    steps = emptyList(),
                    verificationCommands = emptyList(),
                    evidenceIds = emptyList(),
                    uncertainties = emptyList()
                )
            ).jsonObject
            val result = EngineeringContextGateway.changeSafety(root.path, plan)
            val status = result["status"]?.toString()?.trim('"')
            assertEquals("REVIEW_REQUIRED", status)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `change contract gateway returns persisted artifact without regeneration`() {
        val root = Files.createTempDirectory("gateway-contract").toFile()
        try {
            val contract = AgentChangeContract(
                changeSummary = "test change",
                repository = root.canonicalPath,
                preparedHead = "abc123",
                plannedPaths = listOf("src/App.kt"),
                expectedChangeTypes = mapOf("src/App.kt" to listOf("MODIFIED")),
                expectedComponents = listOf("src/App.kt"),
                verificationCommands = listOf("./gradlew test"),
                evidenceIds = listOf("e-1"),
                architectureExpectations = listOf("keep module boundary"),
                fingerprint = "0".repeat(64)
            )
            val file = root.resolve("output/agent-change-contract.json").apply { parentFile.mkdirs() }
            file.writeText(Json { encodeDefaults = true }.encodeToString(AgentChangeContract.serializer(), contract))

            val returned = EngineeringContextGateway.changeContract(root.path)
            val decoded = Json.decodeFromJsonElement(AgentChangeContract.serializer(), returned)
            assertEquals(contract, decoded)
        } finally { root.deleteRecursively() }
    }
}
