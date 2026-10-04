package com.vericore.mcp

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import com.vericore.core.intelligence.EngineeringContextSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpProtocolTest {
    @Test
    fun initializeReturnsProtocolAndVericoreServerIdentity() {
        val response = McpProtocol.handle(buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(1)); put("method", JsonPrimitive("initialize")); put("params", buildJsonObject { put("protocolVersion", JsonPrimitive("2025-06-18")) })
        })
        assertEquals("2.0", response["jsonrpc"]?.toString()?.trim('"'))
        assertEquals("1", response["id"]?.toString())
        val result = response["result"].toString()
        assertTrue(result.contains("tools")); assertTrue(result.contains("Vericore")); assertTrue(!result.contains("CodeContext"))
    }

    @Test
    fun toolsListExposesCanonicalVericoreTools() {
        val response = McpProtocol.handle(buildJsonObject { put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(2)); put("method", JsonPrimitive("tools/list")) })
        val result = response["result"].toString()
        listOf("vericore_analyze_repository", "vericore_impact_analysis", "vericore_architecture_analysis", "vericore_pr_intelligence", "vericore_get_engineering_reality", "vericore_get_context_snapshot", "vericore_get_context_diff", "vericore_get_architecture_drift", "vericore_get_architecture_contract", "vericore_prepare_change", "vericore_get_change_contract", "vericore_get_evidence", "vericore_change_safety", "vericore_verify_change").forEach { assertTrue(result.contains(it), "Missing MCP tool: $it") }
        assertTrue(!result.contains("codecontext_"))
    }

    @Test
    fun legacyCodeContextToolAliasRemainsCallableAndWarns() {
        val originalErr = System.err
        val capturedErr = ByteArrayOutputStream()
        System.setErr(PrintStream(capturedErr))
        try {
            val response = McpProtocol.handle(buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(5)); put("method", JsonPrimitive("tools/call"))
                put("params", buildJsonObject { put("name", JsonPrimitive("codecontext_get_engineering_reality")); put("arguments", buildJsonObject { put("repoPath", JsonPrimitive("https://github.com/spring-projects/spring-petclinic")) }) })
            })
            assertTrue(response.toString().contains("Remote repositories are not supported"))
            val warning = capturedErr.toString()
            assertTrue(warning.contains("Deprecated MCP tool 'codecontext_get_engineering_reality'"))
            assertTrue(warning.contains("migrate to 'vericore_get_engineering_reality'"))
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun prepareChangePersistsContractForImmediateRetrieval() {
        val root = Files.createTempDirectory("vericore-mcp-prepare-").toFile()
        try {
            root.resolve("src/App.kt").apply {
                parentFile.mkdirs()
                writeText("class App")
            }
            Git.init().setDirectory(root).call().use { git ->
                git.add().addFilepattern(".").call()
                git.commit()
                    .setMessage("baseline")
                    .setAuthor(PersonIdent("test", "test@example.com"))
                    .setCommitter(PersonIdent("test", "test@example.com"))
                    .call()
            }

            val prepare = McpProtocol.handle(buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(10))
                put("method", JsonPrimitive("tools/call"))
                put("params", buildJsonObject {
                    put("name", JsonPrimitive("vericore_prepare_change"))
                    put("arguments", buildJsonObject {
                        put("repoPath", JsonPrimitive(root.path))
                        put("changeSummary", JsonPrimitive("update App"))
                    })
                })
            })
            assertTrue(prepare["result"] != null)
            val contextFile = root.resolve("output/engineering-context.json")
            val preparationFile = root.resolve("output/engineering-preparation.json")
            val contractFile = root.resolve("output/agent-change-contract.json")
            assertTrue(contextFile.isFile)
            assertTrue(preparationFile.isFile)
            assertTrue(contractFile.isFile)
            val snapshot = kotlinx.serialization.json.Json.decodeFromString(EngineeringContextSnapshot.serializer(), contextFile.readText())
            assertTrue(snapshot.snapshotDigest.isNotBlank())

            val get = McpProtocol.handle(buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(11))
                put("method", JsonPrimitive("tools/call"))
                put("params", buildJsonObject {
                    put("name", JsonPrimitive("vericore_get_change_contract"))
                    put("arguments", buildJsonObject { put("repoPath", JsonPrimitive(root.path)) })
                })
            })
            val result = get["result"]?.toString() ?: ""
            assertTrue(result.contains("fingerprint"))
            val resultJson = kotlinx.serialization.json.Json.parseToJsonElement(result).jsonObject
            val contentText = resultJson["content"]?.jsonArray?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" } ?: ""
            val returnedContract = kotlinx.serialization.json.Json.parseToJsonElement(contentText).jsonObject
            assertEquals(root.canonicalPath, returnedContract["repository"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unknownMethodReturnsJsonRpcMethodNotFoundError() {
        val response = McpProtocol.handle(buildJsonObject { put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(3)); put("method", JsonPrimitive("does/not/exist")) })
        assertEquals("-32601", response["error"]?.let { it.toString().substringAfter("\"code\":").substringBefore(',').trim() })
    }

    @Test
    fun remoteRepositoryArgumentsAreRejected() {
        val response = McpProtocol.handle(buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(4)); put("method", JsonPrimitive("tools/call"))
            put("params", buildJsonObject { put("name", JsonPrimitive("vericore_get_engineering_reality")); put("arguments", buildJsonObject { put("repoPath", JsonPrimitive("https://github.com/spring-projects/spring-petclinic")) }) })
        })
        assertTrue(response.toString().contains("Remote repositories are not supported"))
    }
}
