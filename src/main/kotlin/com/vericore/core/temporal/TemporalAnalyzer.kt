package com.vericore.core.temporal

import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.DisabledOutputStream

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

@Serializable
data class CodebaseSnapshot(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val commitHash: String,
    val totalFiles: Int,
    val totalLines: Int,
    val topHotspots: List<String>
)

/**
 * Reads repository history without changing the working tree.
 *
 * Snapshots are sampled from existing commits and analyzed directly from Git trees. This makes
 * temporal analysis safe for dirty working trees and avoids the destructive checkout behavior of
 * the original MVP implementation.
 */
class TemporalAnalyzer(private val repoPath: String) {

    fun analyzeEvolution(monthsBack: Int = 6, intervalDays: Int = 30): List<CodebaseSnapshot> {
        require(monthsBack >= 0) { "monthsBack must be >= 0" }
        require(intervalDays > 0) { "intervalDays must be > 0" }

        Git.open(File(repoPath)).use { git ->
            val repository = git.repository
            repository.resolve("HEAD") ?: return emptyList()
            val commits = git.log().call().toList()
            if (commits.isEmpty()) return emptyList()

            val headCommit = commits.first()
            val now = Instant.ofEpochSecond(headCommit.commitTime.toLong())
            val cutoff = now.minus(monthsBack.toLong() * 30L, ChronoUnit.DAYS)
            val historyWindow = commits.filter { commit ->
                val timestamp = Instant.ofEpochSecond(commit.commitTime.toLong())
                !timestamp.isBefore(cutoff) && !timestamp.isAfter(now)
            }
            val sampled = sampleCommits(historyWindow, now, cutoff, intervalDays)
            if (sampled.isEmpty()) return emptyList()

            val touchCountsByCommit = buildHistoricalTouchCounts(repository, commits)
            return sampled.sortedBy { it.commitTime }.map { commit ->
                analyzeSnapshot(commit, touchCountsByCommit[commit.name].orEmpty())
            }
        }
    }

    private fun sampleCommits(
        commits: List<RevCommit>,
        now: Instant,
        cutoff: Instant,
        intervalDays: Int
    ): List<RevCommit> {
        if (commits.isEmpty()) return emptyList()

        val selected = linkedMapOf<String, RevCommit>()
        var target = now
        while (!target.isBefore(cutoff)) {
            commits.minByOrNull { kotlin.math.abs(it.commitTime.toLong() - target.epochSecond) }
                ?.let { selected[it.name] = it }
            target = target.minus(intervalDays.toLong(), ChronoUnit.DAYS)
        }
        return selected.values.toList()
    }

    /**
     * Computes cumulative source-file touch counts at each commit using Git's tree diff.
     * The result is keyed by commit hash so snapshot hotspot rankings are deterministic.
     */
    private fun buildHistoricalTouchCounts(
        repository: org.eclipse.jgit.lib.Repository,
        commits: List<RevCommit>
    ): Map<String, Map<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        val result = mutableMapOf<String, Map<String, Int>>()
        val formatter = DiffFormatter(DisabledOutputStream.INSTANCE)
        formatter.setRepository(repository)
        formatter.setDiffComparator(RawTextComparator.DEFAULT)
        formatter.isDetectRenames = true

        try {
            commits.asReversed().forEach { commit ->
                if (commit.parentCount > 0) {
                    formatter.scan(commit.getParent(0).tree, commit.tree).forEach { diff ->
                        val path = if (diff.changeType == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE) {
                            diff.oldPath
                        } else {
                            diff.newPath
                        }
                        if (isSourceFile(path)) counts[path] = (counts[path] ?: 0) + 1
                    }
                }
                result[commit.name] = counts.toMap()
            }
        } finally {
            formatter.close()
        }
        return result
    }

    private fun analyzeSnapshot(commit: RevCommit, touchCounts: Map<String, Int>): CodebaseSnapshot {
        Git.open(File(repoPath)).use { git ->
            val repository = git.repository
            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(commit.tree)
            treeWalk.isRecursive = true

            var fileCount = 0
            var lineCount = 0
            val fileSizes = mutableMapOf<String, Long>()

            repository.newObjectReader().use { reader ->
                while (treeWalk.next()) {
                    val path = treeWalk.pathString
                    if (!isSourceFile(path)) continue
                    val bytes = reader.open(treeWalk.getObjectId(0)).bytes
                    fileCount++
                    lineCount += countLines(bytes)
                    fileSizes[path] = bytes.size.toLong()
                }
            }
            treeWalk.close()

            val hotspots = touchCounts.entries
                .filter { isSourceFile(it.key) }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(10)
                .map { it.key }
                .ifEmpty {
                    fileSizes.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                        .take(10)
                        .map { it.key }
                }

            return CodebaseSnapshot(
                timestamp = Instant.ofEpochSecond(commit.commitTime.toLong()),
                commitHash = commit.name,
                totalFiles = fileCount,
                totalLines = lineCount,
                topHotspots = hotspots
            )
        }
    }

    private fun isSourceFile(path: String): Boolean =
        path.endsWith(".kt") || path.endsWith(".kts") || path.endsWith(".java")

    private fun countLines(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        var lines = 1
        for (byte in bytes) if (byte == '\n'.code.toByte()) lines++
        if (bytes.last() == '\n'.code.toByte()) lines--
        return lines
    }
}
