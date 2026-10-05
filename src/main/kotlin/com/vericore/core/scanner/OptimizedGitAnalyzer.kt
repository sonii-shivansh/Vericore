package com.vericore.core.scanner

import com.vericore.core.config.ConfigLoader
import com.vericore.core.parser.GitMetadata
import com.vericore.core.parser.ParsedFile
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilterGroup

class OptimizedGitAnalyzer {
    fun analyze(repoPath: String, files: List<ParsedFile>): List<ParsedFile> {
        val gitDir = File(repoPath, ".git")
        if (!gitDir.exists()) {
            System.err.println("⚠️ No .git directory found. Skipping Git analysis.")
            return files
        }

        try {
            val repository: Repository =
                    FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build()
            val git = Git(repository)
            val fileStats = mutableMapOf<String, FileChangeStats>()
            val config = ConfigLoader.loadForRepository(repoPath)
            val commitLimit = config.gitCommitLimit.coerceAtLeast(1)
            val relevantPaths = files
                    .map { getRelativePath(File(repoPath), it.file) }
                    .map { it.replace("\\", "/") }
                    .toSet()
            val commits = git.log().call().take(commitLimit + 1).toList()
            val truncated = commits.size > commitLimit
            val commitsToAnalyze = commits.take(commitLimit)
            val totalCommits = commitsToAnalyze.size

            System.err.println("🔍 Analyzing $totalCommits commits (limit: $commitLimit)...")

            if (truncated) {
                System.err.println(
                        "⚠️  Reached commit limit. History analysis is bounded at $commitLimit commits; " +
                                "increase gitCommitLimit in config for deeper history."
                )
            }

            commitsToAnalyze.forEachIndexed { index, commit ->
                if (index % 100 == 0 && index > 0) {
                    val progress = (index * 100) / totalCommits
                    System.err.println("   Progress: $progress% ($index/$totalCommits commits)")
                }

                val parent = if (commit.parentCount > 0) commit.getParent(0) else null
                if (parent != null) {
                    val oldTree = parent.tree
                    val newTree = commit.tree
                    val diffCommand =
                            git.diff()
                                    .setOldTree(prepareTreeParser(repository, oldTree))
                                    .setNewTree(prepareTreeParser(repository, newTree))
                    if (relevantPaths.isNotEmpty()) {
                        diffCommand.setPathFilter(PathFilterGroup.createFromStrings(relevantPaths))
                    }
                    val diffs = diffCommand.call()

                    diffs.forEach { diff ->
                        val path =
                                if (diff.changeType == DiffEntry.ChangeType.DELETE) diff.oldPath
                                else diff.newPath
                        val normalizedPath = path.replace("\\", "/")
                        if (normalizedPath !in relevantPaths) return@forEach
                        val stats = fileStats.getOrPut(normalizedPath) { FileChangeStats() }
                        stats.changes++
                        stats.lastModified = maxOf(stats.lastModified, commit.commitTime.toLong() * 1000)
                        stats.authors.add(commit.authorIdent.name)
                        stats.messages.add(commit.shortMessage)
                    }
                }
            }

            repository.close()

            return files.map { parsed ->
                val relativePath = getRelativePath(File(repoPath), parsed.file)
                val stats = fileStats[relativePath] ?: fileStats[relativePath.replace("\\", "/")]
                if (stats != null) {
                    val topAuthors =
                            stats.authors
                                    .groupBy { it }
                                    .mapValues { it.value.size }
                                    .entries
                                    .sortedByDescending { it.value }
                                    .take(3)
                                    .map { it.key }
                    parsed.copy(
                            gitMetadata =
                                    GitMetadata(
                                            lastModified = stats.lastModified,
                                            changeFrequency = stats.changes,
                                            topAuthors = topAuthors,
                                            recentMessages = stats.messages.take(3)
                                    )
                    )
                } else {
                    parsed
                }
            }
        } catch (e: Exception) {
            System.err.println("Git analysis failed: ${e.message}")
            return files
        }
    }

    private fun prepareTreeParser(
            repository: Repository,
            tree: org.eclipse.jgit.lib.ObjectId
    ): org.eclipse.jgit.treewalk.AbstractTreeIterator {
        val treeWalk = TreeWalk(repository)
        treeWalk.addTree(tree)
        treeWalk.isRecursive = true
        val parser = CanonicalTreeParser()
        parser.reset(repository.newObjectReader(), tree)
        return parser
    }

    private fun getRelativePath(base: File, file: File): String {
        return file.absolutePath
                .substring(base.absolutePath.length + 1)
                .replace("\\", "/")
    }

    private data class FileChangeStats(
            var changes: Int = 0,
            var lastModified: Long = 0,
            val authors: MutableSet<String> = mutableSetOf(),
            val messages: MutableList<String> = mutableListOf()
    )
}
