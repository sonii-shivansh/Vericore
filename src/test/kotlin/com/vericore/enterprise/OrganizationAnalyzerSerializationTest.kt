package com.vericore.enterprise

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrganizationAnalyzerSerializationTest {
    @Test
    fun `organization result is serializable for REST responses`() {
        val result = RepoResult(
            name = "payments",
            fileCount = 12,
            hotspots = listOf(RepoHotspot("PaymentService.kt", 0.42)),
        )

        val encoded = Json.encodeToString(result)

        assertTrue(encoded.contains("\"name\":\"payments\""))
        assertTrue(encoded.contains("\"fileCount\":12"))
        assertTrue(encoded.contains("\"file\":\"PaymentService.kt\""))
        assertEquals(result, Json.decodeFromString<RepoResult>(encoded))
    }

    @Test
    fun `organization error messages do not expose internal exception details`() {
        val internalPathError = IllegalStateException("Failed to read /home/private/repository/.vericore.json")
        val invalidConfigError = IllegalArgumentException("Invalid value in /tmp/private/config.json")
        val fileLimitError = IllegalArgumentException("Repository exceeds the maximum file limit: 5000")

        assertEquals("Repository analysis failed", safeRepositoryAnalysisError(internalPathError))
        assertEquals("Repository analysis failed", safeRepositoryAnalysisError(invalidConfigError))
        assertEquals("Repository exceeds the configured maximum file limit", safeRepositoryAnalysisError(fileLimitError))
    }
}