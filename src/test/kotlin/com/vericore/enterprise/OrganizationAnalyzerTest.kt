package com.vericore.enterprise

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class OrganizationAnalyzerTest {
    @Test
    fun `organization result is serializable for REST responses`() {
        val result = listOf(
            RepoResult(
                name = "payment-service",
                fileCount = 3,
                hotspots = listOf(RepoHotspot("src/PaymentService.kt", 0.42))
            )
        )

        val encoded = Json.encodeToString(result)

        assertTrue(encoded.contains("payment-service"))
        assertTrue(encoded.contains("PaymentService.kt"))
    }
}
