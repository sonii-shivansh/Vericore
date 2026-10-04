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
            hotspots = listOf("PaymentService.kt" to 0.42),
        )

        val encoded = Json.encodeToString(result)

        assertTrue(encoded.contains("\"name\":\"payments\""))
        assertTrue(encoded.contains("\"fileCount\":12"))
        assertEquals(result, Json.decodeFromString<RepoResult>(encoded))
    }
}
