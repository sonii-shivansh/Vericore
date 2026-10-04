package com.vericore.cli

import com.vericore.core.cache.CacheManager
import com.vericore.core.parser.ParsedFile
import com.vericore.core.parser.ParserFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class CodeParallelParser(private val cacheManager: CacheManager? = null) {
    companion object {
        const val DEFAULT_PARSER_CONCURRENCY = 8
        const val MAX_PARSER_CONCURRENCY = 32
    }
    @Volatile
    var lastWarningCount: Int = 0
        private set

    suspend fun parseFiles(files: List<File>): List<ParsedFile> = coroutineScope {
        if (files.isEmpty()) {
            lastWarningCount = 0
            return@coroutineScope emptyList()
        }

        val concurrency = System.getenv("VERICORE_PARSER_CONCURRENCY")
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_PARSER_CONCURRENCY)
            ?: DEFAULT_PARSER_CONCURRENCY
        val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)

        val processed = AtomicInteger(0)
        val warnings = AtomicInteger(0)
        val total = files.size

        val parsedFiles = files.map { file ->
                async(dispatcher) {
                    try {
                        cacheManager?.getCachedParse(file)?.let { cached ->
                            cached.parseWarning?.let { warnings.incrementAndGet() }
                            val count = processed.incrementAndGet()
                            if (count % 100 == 0 || count == total) {
                                System.err.println("   Progress: $count/$total files")
                            }
                            return@async cached
                        }

                        val parser = ParserFactory.getParser(file)
                        val parsed = parser.parse(file)
                        if (parsed.parseWarning != null) warnings.incrementAndGet()

                        cacheManager?.saveParse(file, parsed)

                        val count = processed.incrementAndGet()
                        if (count % 100 == 0 || count == total) {
                            System.err.println("   Progress: $count/$total files")
                        }

                        parsed
                    } catch (e: Exception) {
                        warnings.incrementAndGet()
                        System.err.println("⚠️  Failed to parse ${file.name}: ${e.message}")
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        lastWarningCount = warnings.get()
        if (lastWarningCount > 0) {
            System.err.println("⚠️  Parser diagnostics: $lastWarningCount file(s) reported parsing warnings.")
        }

        parsedFiles
    }
}
