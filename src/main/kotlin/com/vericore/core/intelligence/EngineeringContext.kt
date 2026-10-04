package com.vericore.core.intelligence

import com.vericore.core.scanner.RepositoryScanner
import com.vericore.core.workflow.RepositoryState
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val ENGINEERING_CONTEXT_SCHEMA_VERSION = "1.0"

@Serializable
data class ContextFile(
    val path: String,
    val sha256: String,
    val sizeBytes: Long
)

@Serializable
data class EngineeringContextSnapshot(
    val schemaVersion: String,
    val repositoryCommit: String?,
    val files: List<ContextFile>,
    val totalFiles: Int,
    val totalBytes: Long,
    val languages: List<String>,
    val dirty: Boolean,
    val changedPaths: List<String>,
    val snapshotDigest: String
)

@Serializable
data class ContextDiffSummary(
    val added: Int,
    val removed: Int,
    val modified: Int,
    val unchanged: Int
)

@Serializable
data class ContextDiffChange(
    val type: String,
    val path: String,
    val beforeSha256: String? = null,
    val afterSha256: String? = null
)

@Serializable
data class EngineeringContextDiff(
    val schemaVersion: String,
    val beforeCommit: String?,
    val afterCommit: String?,
    val summary: ContextDiffSummary,
    val changes: List<ContextDiffChange>
)

object EngineeringContextEngine {
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    fun snapshot(root: File, scanner: RepositoryScanner): EngineeringContextSnapshot {
        val repositoryRoot = root.canonicalFile
        require(repositoryRoot.isDirectory) { "Repository path is not a directory: ${root.path}" }
        val repositoryPath = repositoryRoot.toPath().normalize()
        val files = scanner.scan(repositoryRoot.path).map { it.canonicalFile }
            .sortedBy { it.path }
            .filter { file ->
                val filePath = file.toPath().normalize()
                filePath.startsWith(repositoryPath) && !isGeneratedPath(repositoryPath.relativize(filePath).toString().replace(File.separatorChar, '/'))
            }
        val contextFiles = files.map { file ->
            val relative = repositoryPath.relativize(file.toPath().normalize()).toString().replace(File.separatorChar, '/')
            ContextFile(relative, sha256(file.readBytes()), file.length())
        }
        val gitState = runCatching {
            val commit = RepositoryState.head(repositoryRoot.path)
            val changed = GitChangeSetBuilder.fromWorkingTree(repositoryRoot.path)
                .files
                .flatMap { listOfNotNull(it.path, it.oldPath) }
                .map { it.replace(File.separatorChar, '/') }
                .filterNot(::isGeneratedPath)
                .distinct()
                .sorted()
            commit to changed
        }.getOrNull()
        val languages = contextFiles.mapNotNull { file ->
            when (file.path.substringAfterLast('.', "").lowercase()) {
                "java" -> "Java"
                "kt", "kts" -> "Kotlin"
                else -> null
            }
        }.distinct().sorted()
        val digestInput = contextFiles.joinToString("\n") { "${it.path}|${it.sha256}|${it.sizeBytes}" }
        return EngineeringContextSnapshot(
            schemaVersion = ENGINEERING_CONTEXT_SCHEMA_VERSION,
            repositoryCommit = gitState?.first,
            files = contextFiles,
            totalFiles = contextFiles.size,
            totalBytes = contextFiles.sumOf { it.sizeBytes },
            languages = languages,
            dirty = !gitState?.second.isNullOrEmpty(),
            changedPaths = gitState?.second ?: emptyList(),
            snapshotDigest = sha256(digestInput)
        )
    }

    fun diff(before: EngineeringContextSnapshot, after: EngineeringContextSnapshot): EngineeringContextDiff {
        require(before.schemaVersion == ENGINEERING_CONTEXT_SCHEMA_VERSION) { "Unsupported before snapshot schema: ${before.schemaVersion}" }
        require(after.schemaVersion == ENGINEERING_CONTEXT_SCHEMA_VERSION) { "Unsupported after snapshot schema: ${after.schemaVersion}" }
        val beforeByPath = before.files.associateBy { it.path }
        val afterByPath = after.files.associateBy { it.path }
        val paths = (beforeByPath.keys + afterByPath.keys).toSortedSet()
        val changes = buildList {
            for (path in paths) {
                val old = beforeByPath[path]
                val new = afterByPath[path]
                when {
                    old == null && new != null -> add(ContextDiffChange("ADDED", path, afterSha256 = new.sha256))
                    old != null && new == null -> add(ContextDiffChange("REMOVED", path, beforeSha256 = old.sha256))
                    old != null && new != null && old.sha256 != new.sha256 -> add(ContextDiffChange("MODIFIED", path, old.sha256, new.sha256))
                }
            }
        }
        val added = changes.count { it.type == "ADDED" }
        val removed = changes.count { it.type == "REMOVED" }
        val modified = changes.count { it.type == "MODIFIED" }
        return EngineeringContextDiff(
            schemaVersion = ENGINEERING_CONTEXT_SCHEMA_VERSION,
            beforeCommit = before.repositoryCommit,
            afterCommit = after.repositoryCommit,
            summary = ContextDiffSummary(added, removed, modified, paths.size - changes.size),
            changes = changes
        )
    }

    fun encode(snapshot: EngineeringContextSnapshot): String = json.encodeToString(EngineeringContextSnapshot.serializer(), snapshot)
    fun encode(diff: EngineeringContextDiff): String = json.encodeToString(diff)

    private fun isGeneratedPath(path: String): Boolean {
        val normalized = path.replace(File.separatorChar, '/').trimStart('/')
        return normalized == ".vericore" || normalized.startsWith(".vericore/") ||
            normalized == ".vericore-architecture-contract.json" ||
            normalized == ".codecontext" || normalized.startsWith(".codecontext/") ||
            normalized == ".codecontext-architecture-contract.json" ||
            normalized == "output" || normalized.startsWith("output/")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun sha256(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))
}
