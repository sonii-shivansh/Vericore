package com.vericore.core.intelligence

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser

/** Converts JGit changes into a stable repository-relative ChangeSet. */
object GitChangeSetBuilder {
    fun fromWorkingTree(repoPath: String): ChangeSet {
        openRepository(repoPath).use { repository ->
            Git(repository).use { git ->
                val status = git.status().call()
                val stagedChanges = status.added + status.changed + status.removed
                val workingChanges = status.modified + status.missing
                val untrackedChanges = status.untracked

                // Release-gate and preparation runs create output/.vericore artifacts that are
                // intentionally outside the mutation scope. If nothing else changed, avoid the
                // two full-tree JGit diff scans that the old implementation performed.
                if (listOf(stagedChanges, workingChanges, untrackedChanges)
                        .flatten()
                        .none(::isMeaningfulPath)) {
                    return ChangeSet(emptyList(), source = "working-tree")
                }

                val entries = mutableListOf<DiffEntry>()
                if (stagedChanges.any(::isMeaningfulPath)) {
                    entries += git.diff().setCached(true).call()
                }
                if (workingChanges.any(::isMeaningfulPath)) {
                    entries += git.diff().setCached(false).call()
                }

                val changes = entries
                    .map { toChangedFile(it, repository) }
                    .filterNot { isToolGeneratedPath(it.path) }
                    .toMutableList()
                val trackedPaths = changes.flatMap { listOfNotNull(it.path, it.oldPath) }.toSet()

                untrackedChanges
                    .filter { it !in trackedPaths }
                    .filter(::isMeaningfulPath)
                    .sorted()
                    .forEach { path ->
                        changes += ChangedFile(path, ChangeType.ADDED, additions = countLines(File(repository.workTree, path)))
                    }

                return ChangeSet(mergeDuplicateChanges(changes), source = "working-tree")
            }
        }
    }

    fun fromRevisions(repoPath: String, baseRevision: String, headRevision: String): ChangeSet {
        require(baseRevision.isNotBlank() && headRevision.isNotBlank()) { "Base and head revisions are required" }
        openRepository(repoPath).use { repository ->
            val baseTree = resolveTree(repository, baseRevision)
            val headTree = resolveTree(repository, headRevision)
            val entries = repository.newObjectReader().use { reader ->
                val oldParser = CanonicalTreeParser(null, reader, baseTree)
                val newParser = CanonicalTreeParser(null, reader, headTree)
                DiffFormatter(ByteArrayOutputStream()).use { formatter ->
                    formatter.setRepository(repository)
                    formatter.setDiffComparator(RawTextComparator.DEFAULT)
                    formatter.setDetectRenames(true)
                    formatter.scan(oldParser, newParser)
                }
            }
            return ChangeSet(
                entries.map { toChangedFile(it, repository) }
                    .sortedWith(compareBy<ChangedFile> { it.path }.thenBy { it.changeType.name }),
                source = "$baseRevision..$headRevision"
            )
        }
    }

    private fun toChangedFile(entry: DiffEntry, repository: Repository): ChangedFile {
        val path = if (entry.changeType == DiffEntry.ChangeType.DELETE) entry.oldPath else entry.newPath
        val type = when (entry.changeType) {
            DiffEntry.ChangeType.ADD -> ChangeType.ADDED
            DiffEntry.ChangeType.MODIFY -> ChangeType.MODIFIED
            DiffEntry.ChangeType.DELETE -> ChangeType.DELETED
            DiffEntry.ChangeType.RENAME -> ChangeType.RENAMED
            DiffEntry.ChangeType.COPY -> ChangeType.COPIED
        }
        val (additions, deletions) = countEdits(entry, repository)
        return ChangedFile(
            path = path.replace('\\', '/'),
            changeType = type,
            oldPath = entry.oldPath.takeUnless { it == DiffEntry.DEV_NULL }?.replace('\\', '/'),
            additions = additions,
            deletions = deletions
        )
    }

    private fun countEdits(entry: DiffEntry, repository: Repository): Pair<Int, Int> = runCatching {
        DiffFormatter(ByteArrayOutputStream()).use { formatter ->
            formatter.setRepository(repository)
            formatter.setDiffComparator(RawTextComparator.DEFAULT)
            val edits = formatter.toFileHeader(entry).toEditList()
            edits.sumOf { it.endB - it.beginB } to edits.sumOf { it.endA - it.beginA }
        }
    }.getOrDefault(0 to 0)

    private fun mergeDuplicateChanges(changes: List<ChangedFile>): List<ChangedFile> {
        val priority = listOf(ChangeType.DELETED, ChangeType.RENAMED, ChangeType.COPIED, ChangeType.ADDED, ChangeType.MODIFIED)
        return changes.groupBy { it.path }
            .map { (path, items) ->
                val type = items.minBy { priority.indexOf(it.changeType).takeIf { index -> index >= 0 } ?: priority.size }.changeType
                ChangedFile(
                    path = path,
                    changeType = type,
                    oldPath = items.firstNotNullOfOrNull { it.oldPath },
                    additions = items.sumOf { it.additions },
                    deletions = items.sumOf { it.deletions }
                )
            }
            .sortedWith(compareBy<ChangedFile> { it.path }.thenBy { it.changeType.name })
    }

    /** Paths owned by Vericore itself must not become repository change evidence. */
    private fun isToolGeneratedPath(path: String): Boolean {
        val normalized = path.replace('\\', '/').trimStart('/')
        return normalized == ".vericore" ||
            normalized.startsWith(".vericore/") ||
            normalized == ".codecontext" ||
            normalized.startsWith(".codecontext/") ||
            normalized == "output" ||
            normalized.startsWith("output/")
    }

    private fun isMeaningfulPath(path: String): Boolean = !isToolGeneratedPath(path)

    private fun resolveTree(repository: Repository, revision: String): ObjectId =
        repository.resolve("$revision^{tree}") ?: throw IllegalArgumentException("Invalid revision: $revision")

    private fun countLines(file: File): Int {
        if (!file.isFile || !file.canRead()) return 0
        return runCatching { file.readLines(StandardCharsets.UTF_8).size }.getOrDefault(0)
    }

    private fun openRepository(repoPath: String): Repository {
        val root = File(repoPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repoPath" }
        return runCatching {
            FileRepositoryBuilder().setWorkTree(root).readEnvironment().findGitDir(root).build()
        }.getOrElse { throw IllegalArgumentException("Not a Git repository: $repoPath") }
    }
}
