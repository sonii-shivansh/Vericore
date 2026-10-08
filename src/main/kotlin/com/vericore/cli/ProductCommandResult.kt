package com.vericore.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProductCommandResult(
    val schemaVersion: String = "1.0",
    val command: String,
    val status: String,
    val repository: String? = null,
    val findings: List<ProductFinding> = emptyList(),
    val artifacts: List<ProductArtifact> = emptyList(),
    val nextStep: String? = null
)

@Serializable
data class ProductFinding(
    val level: String,
    val message: String
)

@Serializable
data class ProductArtifact(
    val kind: String,
    val path: String
)

fun productJson(result: ProductCommandResult): String =
    Json { prettyPrint = true; encodeDefaults = true }.encodeToString(result)
