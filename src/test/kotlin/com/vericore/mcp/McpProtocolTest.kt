package com.vericore.mcp

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
        listOf("vericore_analyze_repository", "vericore_impact_analysis", "vericore_architecture_analysis", "vericore_pr_intelligence", "vericore_review", "vericore_recommendations", "vericore_get_engineering_reality", "vericore_get_context_snapshot", "vericore_get_context_diff", "vericore_get_architecture_drift", "vericore_get_architecture_contract", "vericore_prepare_change", "vericore_get_change_contract", "vericore_get_evidence", "vericore_change_safety", "vericore_verify_change").forEach { assertTrue(result.contains(it), "Missing MCP tool: $it") }
        assertTrue(!result.contains("codecontext_"))
    }

    @Test
    fun reviewToolSchemaExposesOptionalGroundedAi() {
        val response = McpProtocol.handle(buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(6)); put("method", JsonPrimitive("tools/list"))
        })
        val result = response["result"].toString()
        assertTrue(result.contains("includeAi"))
        assertTrue(result.contains("grounded AI review"))
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
    fun verifyChangeWithoutPlanReturnsInvalidParameters() {
        val response = McpProtocol.handle(buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0")); put("id", JsonPrimitive(7)); put("method", JsonPrimitive("tools/call"))
            put("params", buildJsonObject {
                put("name", JsonPrimitive("vericore_verify_change"))
                put("arguments", buildJsonObject { put("repoPath", JsonPrimitive(".")) })
            })
        })
        assertEquals("-32602", response["error"]?.let { it.toString().substringAfter("\\"code\\":").substringBefore(',').trim() })
        assertTrue(response.toString().contains("plan is required"))
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
