package com.vericore.core.cache

import com.vericore.core.parser.ParsedFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CacheManager(private val cacheDir: File = File(DEFAULT_CACHE_DIR)) {
    companion object {
        const val DEFAULT_CACHE_DIR = ".vericore/cache"

        /** Resolve persistent parse-cache state inside the repository being analyzed. */
        fun forRepository(root: File): CacheManager = CacheManager(File(root, DEFAULT_CACHE_DIR))
    }

    init { if (!cacheDir.exists()) cacheDir.mkdirs() }
    private val locks = ConcurrentHashMap<String, ReentrantReadWriteLock>()
    private fun getLock(key: String) = locks.computeIfAbsent(key) { ReentrantReadWriteLock() }

    fun getCachedParse(file: File): ParsedFile? {
        val cacheKey = getCacheKey(file)
        val cacheFile = File(cacheDir, "$cacheKey.json")
        return getLock(cacheKey).read {
            if (!cacheFile.exists()) return@read null
            try { Json.decodeFromString<ParsedFile>(cacheFile.readText()) }
            catch (_: Exception) { cacheFile.delete(); null }
        }
    }

    fun saveParse(file: File, parsed: ParsedFile) {
        val cacheKey = getCacheKey(file)
        val cacheFile = File(cacheDir, "$cacheKey.json")
        getLock(cacheKey).write {
            try {
                val temp = File(cacheFile.absolutePath + ".tmp")
                temp.writeText(Json.encodeToString(parsed))
                runCatching {
                    Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }.getOrElse {
                    Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                System.err.println("Failed to cache ${file.name}: ${e.message}")
            }
        }
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
        val metadata = "${file.canonicalPath}:$contentHash"
        return MessageDigest.getInstance("SHA-256")
            .digest(metadata.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
        locks.clear()
    }
}
