package com.vericore.core.cache

import com.vericore.core.parser.ParsedFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CacheManager(private val cacheDir: File = defaultCacheDirectory()) {
    init { if (!cacheDir.exists()) cacheDir.mkdirs() }

    private val locks = ConcurrentHashMap<String, ReentrantReadWriteLock>()
    private fun getLock(key: String) = locks.computeIfAbsent(key) { ReentrantReadWriteLock() }

    fun getCachedParse(file: File): ParsedFile? {
        val cacheKey = getCacheKey(file)
        val cacheFile = File(cacheDir, "\${cacheKey}.json")
        return getLock(cacheKey).read {
            if (!cacheFile.exists()) return@read null
            try {
                Json.decodeFromString<ParsedFile>(cacheFile.readText())
            } catch (_: Exception) {
                cacheFile.delete()
                null
            }
        }
    }

    fun saveParse(file: File, parsed: ParsedFile) {
        val cacheKey = getCacheKey(file)
        val cacheFile = File(cacheDir, "\${cacheKey}.json")
        getLock(cacheKey).write {
            try {
                val temp = File(cacheFile.absolutePath + ".tmp")
                temp.writeText(Json.encodeToString(parsed))
                runCatching {
                    Files.move(
                        temp.toPath(),
                        cacheFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }.getOrElse {
                    Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                System.err.println("Failed to cache \${file.name}: \${e.message}")
            }
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
        locks.clear()
    }

    companion object {
        const val CACHE_DIR_ENV = "VERICORE_CACHE_DIR"

        /**
         * Returns a cache scoped to one repository. The default cache root lives
         * outside the repository so normal analysis cannot create .vericore files.
         */
        fun forRepository(repoPath: String): CacheManager {
            val root = File(repoPath).canonicalFile
            require(root.isDirectory) { "Repository path is not a directory: \$repoPath" }
            val scope = sha256(root.path).take(24)
            return CacheManager(File(defaultCacheDirectory(), scope))
        }

        fun defaultCacheDirectory(): File {
            val configured = System.getenv(CACHE_DIR_ENV)?.trim().takeIf { !it.isNullOrEmpty() }
            if (configured != null) return File(configured)

            val xdg = System.getenv("XDG_CACHE_HOME")?.trim().takeIf { !it.isNullOrEmpty() }
            val base = if (xdg != null) File(xdg) else File(System.getProperty("user.home"), ".cache")
            return File(base, "vericore/cache")
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private fun getCacheKey(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        val contentHash = digest.digest().joinToString("") { "%02x".format(it) }
        val metadata = "\${file.canonicalPath}:\$contentHash"
        return MessageDigest.getInstance("SHA-256")
            .digest(metadata.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
