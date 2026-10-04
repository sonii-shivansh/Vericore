package com.vericore.enterprise

import com.vericore.cli.CodeParallelParser
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.VericoreConfig
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

@Serializable
data class RepoHotspot(
    val file: String,
    val score: Double
)

@Serializable
data class RepoResult(
    val name: String,
    val fileCount: Int,
    val hotspots: List<RepoHotspot>,
    val error: String? = null
)

class OrganizationAnalyzer(private val maxConcurrentRepositories: Int = 2) {
    suspend fun analyzeRepositories(repoPaths: List<String>, config: VericoreConfig? = null): List<RepoResult> = coroutineScope {
        require(maxConcurrentRepositories > 0) { "maxConcurrentRepositories must be positive" }
        echo("🏢 Starting Organization Analysis for ${repoPaths.size} repositories...")
        val semaphore = Semaphore(maxConcurrentRepositories)
        repoPaths.map { path -> async { semaphore.withPermit { analyzeSingleRepo(path, config) } } }.awaitAll()
    }

    private suspend fun analyzeSingleRepo(path: String, sharedConfig: VericoreConfig?): RepoResult {
        return try {
            val file = File(path).canonicalFile
            if (!file.isDirectory || !file.canRead()) return RepoResult(path, 0, emptyList(), "Path not found or unreadable")
            val config = sharedConfig ?: ConfigLoader.loadForRepository(file.path)
            val files = RepositoryScanner(config).scan(file.path)
            if (files.isEmpty()) return RepoResult(file.name, 0, emptyList(), "No source files")
            require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
            val parsedFiles = CodeParallelParser(CacheManager()).parseFiles(files)
            val graph = RobustDependencyGraph()
            graph.build(parsedFiles)
            graph.analyze()
            RepoResult(
                file.name,
                parsedFiles.size,
                graph.getTopHotspots(5).map { (filePath, score) -> RepoHotspot(File(filePath).name, score) }
            )
        } catch (e: Exception) {
            RepoResult(File(path).name, 0, emptyList(), e.message ?: "Analysis failed")
        }
    }

    private fun echo(msg: String) = println(msg)
}
