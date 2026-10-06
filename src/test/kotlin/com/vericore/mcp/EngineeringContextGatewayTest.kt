package com.vericore.mcp

import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.scanner.RepositoryScanner
import com.vericore.core.config.VericoreConfig
import com.vericore.core.planner.EngineeringPlan
import com.vericore.cli.CodeParallelParser
import com.vericore.core.cache.CacheManager
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.workflow.AgentChangeContract
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.jgit.api.Git
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `change safety fails closed when planned paths are empty`() {
        val root = Files.createTempDirectory("gateway-safety").toFile()
        val git = Git.init().setDirectory(root).call()
        try {
            git.repository.config.setString("user", null, "name", "VCORE CI")
            git.repository.config.setString("user", null, "email", "ci@example.com")
            git.repository.config.save()

            val source = root.resolve("src/App.kt")
            source.parentFile.mkdirs()
            source.writeText("class App")
            git.add().addFilepattern("src/App.kt").call()
            git.commit().setMessage("initial fixture").setAuthor("VCORE CI", "ci@example.com").call()

            source.writeText("class App { fun changed() = true }")

            val plan = EngineeringPlan(
                changeSummary = "test change",
                repository = root.canonicalPath,
                affectedComponents = listOf("src/App.kt"),
                plannedPaths = emptyList(),
                concerns = emptyList(),
                riskLevel = com.vericore.core.planner.RiskLevel.MEDIUM,
                steps = emptyList(),
                verificationCommands = emptyList(),
                evidenceIds = emptyList(),
                uncertainties = emptyList()
            )
            val payload = Json { encodeDefaults = true }
                .encodeToJsonElement(EngineeringPlan.serializer(), plan)
                .jsonObject

            val result = EngineeringContextGateway.changeSafety(root.path, payload)
            assertEquals("FAIL", result["status"]?.jsonPrimitive?.content)
            assertTrue(result["unexpectedPaths"]?.toString()?.contains("src/App.kt") == true)
        } finally {
            git.close()
            root.deleteRecursively()
        }
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
    @Test
    fun `prepare persists the agent verification artifacts for MCP callers`() {
        val root = Files.createTempDirectory("gateway-prepare").toFile()
        val git = Git.init().setDirectory(root).call()
        try {
            git.repository.config.setString("user", null, "name", "VCORE CI")
            git.repository.config.setString("user", null, "email", "ci@example.com")
            git.repository.config.save()

            val source = root.resolve("src/Target.kt")
            source.parentFile.mkdirs()
            source.writeText("class Target { fun value() = \"before\" }")
            git.add().addFilepattern("src/Target.kt").call()
            git.commit().setMessage("initial fixture").setAuthor("VCORE CI", "ci@example.com").call()

            val prepared = EngineeringContextGateway.prepare(root.path, "Update Target safely")
            assertTrue(root.resolve("output/engineering-context.json").isFile)
            assertTrue(root.resolve("output/engineering-plan.json").isFile)
            assertTrue(root.resolve("output/agent-change-contract.json").isFile)

            val returned = Json.decodeFromJsonElement(
                com.vericore.core.workflow.EngineeringPreparationResult.serializer(),
                prepared
            )
            assertEquals(returned.contract.fingerprint, returned.plan.contractFingerprint)
            val persisted = EngineeringContextGateway.changeContract(root.path)
            assertEquals(
                returned.contract,
                Json.decodeFromJsonElement(AgentChangeContract.serializer(), persisted)
            )
        } finally {
            git.close()
            root.deleteRecursively()
        }
    }

}

