package com.vericore.core.workflow

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * A deterministic digest of repository inputs at one observed point in time.
 *
 * Version-control metadata and common generated/build-output directories are excluded.
 * Tracked files and untracked repository inputs are content-hashed, including non-source build
 * inputs. When Git is available, untracked diagnostic files (log/tmp/temp/trace/out) outside
 * source, test, fixture, resource, example, benchmark, and documentation trees are excluded.
 */
data class CandidateStateSnapshot(
    val schemaVersion: String = "1.0",
    val digest: String,
    val fileCount: Int,
    val totalBytes: Long
)

object CandidateStateSnapshotter {
    private val excludedDirectoryNames = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".vericore",
        ".codecontext",
        "build",
        "node_modules",
        "output",
        "target"
    )
    private val sourceOrFixtureDirectoryNames = setOf(
        "src", "test", "tests", "testdata", "test-fixtures", "test-fixture",
        "fixtures", "fixture", "resources", "examples", "benchmark", "docs"
    )
    private val diagnosticOutputExtensions = setOf("log", "tmp", "temp", "trace", "out")

    private data class Entry(
        val path: String,
        val kind: Byte,
        val size: Long,
        val contentDigest: ByteArray,
        val executable: Boolean
    )

    fun capture(repository: File): CandidateStateSnapshot {
        val root = repository.canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: ${repository.path}" }
        val rootPath = root.toPath().normalize()
        val trackedPaths = trackedPaths(root)
        val entries = mutableListOf<Entry>()

        Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != rootPath && dir.fileName?.toString()?.let(excludedDirectoryNames::contains) == true) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile && !attrs.isSymbolicLink) {
                    return FileVisitResult.CONTINUE
                }
                val relativePath = rootPath.relativize(file).toString().replace(File.separatorChar, '/')
                if (isUntrackedDiagnosticOutput(relativePath, trackedPaths)) {
                    return FileVisitResult.CONTINUE
                }
                val entry = if (attrs.isSymbolicLink) {
                    val target = Files.readSymbolicLink(file).toString().toByteArray(Charsets.UTF_8)
                    Entry(relativePath, 'L'.code.toByte(), target.size.toLong(), sha256(target), false)
                } else {
                    hashRegularFile(file, relativePath, attrs)
                }
                entries += entry
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                throw IOException("Unable to snapshot repository candidate file '${file}': ${exc.message}", exc)
            }
        })

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        for (entry in entries.sortedBy { it.path }) {
            val pathBytes = entry.path.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(pathBytes.size).array())
            digest.update(pathBytes)
            digest.update(entry.kind)
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(entry.size).array())
            digest.update(entry.contentDigest)
            digest.update((if (entry.executable) 1 else 0).toByte())
            totalBytes = Math.addExact(totalBytes, entry.size)
        }

        return CandidateStateSnapshot(
            digest = digest.digest().toHex(),
            fileCount = entries.size,
            totalBytes = totalBytes
        )
    }

    /**
     * Untracked diagnostic logs commonly appear as side effects of test commands.
     * Tracked files and files under source/test/fixture trees remain part of the digest.
     * When Git metadata is unavailable, do not make this heuristic exclusion.
     */
    private fun isUntrackedDiagnosticOutput(relativePath: String, trackedPaths: Set<String>?): Boolean {
        if (trackedPaths == null || relativePath in trackedPaths) return false
        val parts = relativePath.split('/')
        if (parts.any { it in sourceOrFixtureDirectoryNames }) return false
        val extension = parts.last().substringAfterLast('.', "").lowercase()
        return extension in diagnosticOutputExtensions
    }

    private fun trackedPaths(root: File): Set<String>? = runCatching {
        val process = ProcessBuilder("git", "ls-files", "--cached", "-z")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes()
        val exitCode = process.waitFor()
        if (exitCode != 0) return@runCatching null
        output.toString(Charsets.UTF_8)
            .split('\u0000')
            .filter(String::isNotEmpty)
            .map { it.replace('\\\\', '/') }
            .toSet()
    }.getOrNull()

    private fun hashRegularFile(path: Path, relativePath: String, initial: BasicFileAttributes): Entry {
        val contentDigest = MessageDigest.getInstance("SHA-256")
        val initialExecutable = Files.isExecutable(path)
        var bytesRead = 0L
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                contentDigest.update(buffer, 0, count)
                bytesRead = Math.addExact(bytesRead, count.toLong())
            }
        }

        val after = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!after.isRegularFile ||
            initial.size() != after.size() ||
            initial.lastModifiedTime() != after.lastModifiedTime() ||
            initial.fileKey() != after.fileKey() ||
            initialExecutable != Files.isExecutable(path) ||
            bytesRead != after.size()
        ) {
            throw IOException("Repository candidate file changed while being snapshotted: $relativePath")
        }

        return Entry(relativePath, 'F'.code.toByte(), bytesRead, contentDigest.digest(), initialExecutable)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
