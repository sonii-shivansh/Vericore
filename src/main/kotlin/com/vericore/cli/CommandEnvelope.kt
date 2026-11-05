package com.vericore.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Standard output formats across all Vericore CLI commands.
 */
enum class OutputFormat {
    HUMAN,
    JSON;

    companion object {
        fun resolve(format: String?, jsonFlag: Boolean = false): OutputFormat {
            val normalized = format?.trim()?.lowercase()
            if (normalized != null && normalized.isNotEmpty() && normalized != "human" && normalized != "json") {
                throw IllegalArgumentException("Unsupported output format: '$format'. Supported formats: human, json")
            }
            if (jsonFlag && normalized == "human") {
                throw IllegalArgumentException("Conflicting output format options: cannot specify both --json and --format human")
            }
            if (jsonFlag || normalized == "json") return JSON
            return HUMAN
        }
    }
}

/**
 * Common status values across Vericore CLI commands.
 */
object CommandStatus {
    const val PASS = "PASS"
    const val SUCCESS = "SUCCESS"
    const val FAIL = "FAIL"
    const val WARNING = "WARNING"
    const val NO_CHANGES = "NO_CHANGES"
    const val NO_FINDINGS = "NO_FINDINGS"
    const val PARTIAL = "PARTIAL"
    const val NOT_EVALUATED = "NOT_EVALUATED"
    const val ERROR = "ERROR"
    const val ALREADY_INITIALIZED = "ALREADY_INITIALIZED"
    const val REVIEW_REQUIRED = "REVIEW_REQUIRED"
}

@Serializable
data class RepositoryInfo(
    val name: String,
    val path: String
)

@Serializable
data class EnvelopeFinding(
    val level: String,
    val message: String,
    val ruleId: String? = null,
    val path: String? = null
)

@Serializable
data class EnvelopeRecommendation(
    val priority: String,
    val title: String,
    val action: String,
    val evidencePaths: List<String> = emptyList()
)

@Serializable
data class EnvelopeArtifact(
    val kind: String,
    val path: String
)

@Serializable
data class ExecutionInfo(
    val exitCode: Int = 0
)

/**
 * Universal JSON envelope contract across all 27 Vericore commands.
 */
@Serializable
data class CommandEnvelope<T>(
    val schemaVersion: String = "1.0",
    val command: String,
    val status: String,
    val summary: String,
    val repository: RepositoryInfo? = null,
    val data: T? = null,
    val findings: List<EnvelopeFinding> = emptyList(),
    val recommendations: List<EnvelopeRecommendation> = emptyList(),
    val artifacts: List<EnvelopeArtifact> = emptyList(),
    val nextStep: String? = null,
    val execution: ExecutionInfo = ExecutionInfo(exitCode = 0)
)

object JsonEnvelope {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    var emitted: Boolean = false

    inline fun <reified T> encode(envelope: CommandEnvelope<T>): String {
        emitted = true
        return json.encodeToString(envelope)
    }

    fun reset() {
        emitted = false
    }
}

/**
 * Standard utility to relativize file paths against repository root, normalizing slashes.
 */
fun repositoryRelativePath(file: File, repositoryRoot: File): String {
    return try {
        val rootPath = repositoryRoot.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        if (filePath.startsWith(rootPath)) {
            rootPath.relativize(filePath).toString().replace(File.separatorChar, '/')
        } else {
            file.path.replace(File.separatorChar, '/')
        }
    } catch (_: Exception) {
        file.path.replace(File.separatorChar, '/')
    }
}

fun repositoryRelativePath(path: String, repositoryRoot: File): String {
    val file = File(path).let { if (it.isAbsolute) it else File(repositoryRoot, path) }
    return repositoryRelativePath(file, repositoryRoot)
}
