package com.vericore.server

import com.vericore.cli.CodeParallelParser
import com.vericore.core.Version
import com.vericore.core.ai.AICodeAnalyzer
import com.vericore.core.ai.CodebaseContext
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.VericoreConfig
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.ArchitectureIntelligenceEngine
import com.vericore.core.intelligence.ArchitectureIntelligenceResult
import com.vericore.core.intelligence.ChangeImpactEngine
import com.vericore.core.intelligence.ChangeImpactResult
import com.vericore.core.intelligence.GitChangeSetBuilder
import com.vericore.core.intelligence.PRIntelligenceAnalyzer
import com.vericore.core.intelligence.PRIntelligenceResult
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable data class AnalysisRequest(val repoPath: String)
@Serializable data class AskRequest(val repoPath: String, val question: String)
@Serializable data class ImpactRequest(val repoPath: String, val changedPaths: List<String>)
@Serializable data class ArchitectureRequest(val repoPath: String)
@Serializable data class PRIntelligenceRequest(val repoPath: String, val baseRevision: String? = null, val headRevision: String? = null)
@Serializable data class AnalysisResponse(val fileCount: Int, val hotspots: List<HotspotInfo>, val reportUrl: String)
@Serializable data class HotspotInfo(val file: String, val score: Double)
@Serializable data class ApiError(val error: String)

private const val MAX_QUESTION_LENGTH = 16_000
private const val MAX_CHANGED_PATHS = 100
private const val MAX_REVISION_LENGTH = 256

fun Application.module() {
    install(ContentNegotiation) { json() }
    configureRateLimiting()

    routing {
        staticFiles("/reports", File("output"))
        get("/") { call.respondText("Vericore API is running. 🚀") }
        get("/health") { call.respond(mapOf("status" to "healthy", "version" to Version.current)) }
        get("/health/live") { call.respond(mapOf("status" to "live")) }
        get("/health/ready") { call.respond(mapOf("status" to "ready")) }

        post("/analyze") {
            try {
                val request = call.receive<AnalysisRequest>()
                require(request.repoPath.isNotBlank()) { "Repository path is invalid" }
                require(!request.repoPath.startsWith("http://", true) && !request.repoPath.startsWith("https://", true)) { "Remote repositories are not supported by this local endpoint" }
                val path = sanitizePath(request.repoPath) ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("Invalid or unsafe repository path"))
                val config = ConfigLoader.loadForRepository(path)
                val (graph, parsedFiles, _) = AnalysisLogic.analyze(path, config)
                val enrichedFiles = OptimizedGitAnalyzer().analyze(path, parsedFiles)
                val reportId = UUID.randomUUID().toString()
                val reportFile = File(path, "output/$reportId.html").apply { parentFile.mkdirs() }
                com.vericore.output.ReportGenerator().generate(graph, reportFile.absolutePath, enrichedFiles, com.vericore.core.generator.LearningPathGenerator().generate(graph))
                val hotspots = graph.getTopHotspots(5).map { HotspotInfo(File(it.first).name, it.second) }
                call.respond(AnalysisResponse(parsedFiles.size, hotspots, "/reports/$reportId.html"))
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                System.err.println("Analysis failed: ${e::class.simpleName}")
                call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError("Analysis failed"))
            }
        }

        post("/impact") {
            try {
                val request = call.receive<ImpactRequest>()
                require(request.changedPaths.isNotEmpty() && request.changedPaths.size <= MAX_CHANGED_PATHS) { "Between 1 and $MAX_CHANGED_PATHS changed paths are required" }
                val path = sanitizePath(request.repoPath) ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("Invalid or unsafe repository path"))
                val config = ConfigLoader.loadForRepository(path)
                val (graph, parsedFiles, _) = AnalysisLogic.analyze(path, config)
                val enrichedFiles = OptimizedGitAnalyzer().analyze(path, parsedFiles)
                val pathLookup = enrichedFiles.associateBy { it.file.absolutePath.replace('\\', '/') }
                val changedAbsolute = request.changedPaths.map { File(path, it).absolutePath.replace('\\', '/') }
                val result: ChangeImpactResult = ChangeImpactEngine.analyze(graph.graph, changedAbsolute, graph.pageRankScores, pathLookup.mapValues { it.value.gitMetadata.changeFrequency }, pathLookup.mapValues { it.value.packageName })
                call.respond(result)
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                System.err.println("Impact analysis failed: ${e::class.simpleName}")
                call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError("Impact analysis failed"))
            }
        }

        post("/architecture") {
            try {
                val request = call.receive<ArchitectureRequest>()
                require(request.repoPath.isNotBlank()) { "Repository path is invalid" }
                val path = sanitizePath(request.repoPath) ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("Invalid or unsafe repository path"))
                val config = ConfigLoader.loadForRepository(path)
                val (graph, _, _) = AnalysisLogic.analyze(path, config)
                val result: ArchitectureIntelligenceResult = ArchitectureIntelligenceEngine.analyze(graph.graph, File(path), config.architecture)
                call.respond(result)
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                System.err.println("Architecture analysis failed: ${e::class.simpleName}")
                call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError("Architecture analysis failed"))
            }
        }

        post("/pr-intelligence") {
            try {
                val request = call.receive<PRIntelligenceRequest>()
                require(request.repoPath.isNotBlank()) { "Repository path is invalid" }
                require(request.repoPath.length <= 4096) { "Repository path is invalid" }
                require(!request.repoPath.startsWith("http://", true) && !request.repoPath.startsWith("https://", true)) { "Remote repositories are not supported by this local endpoint" }
                validateRevisionPair(request.baseRevision, request.headRevision)
                val path = sanitizePath(request.repoPath) ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("Invalid or unsafe repository path"))
                val changeSet = if (request.baseRevision != null) GitChangeSetBuilder.fromRevisions(path, request.baseRevision, request.headRevision!!) else GitChangeSetBuilder.fromWorkingTree(path)
                val result: PRIntelligenceResult = PRIntelligenceAnalyzer.analyze(path, changeSet, ConfigLoader.loadForRepository(path))
                call.respond(result)
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                System.err.println("PR intelligence failed: ${e::class.simpleName}")
                call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError("PR intelligence analysis failed"))
            }
        }

        post("/ask") {
            try {
                val request = call.receive<AskRequest>()
                require(request.question.isNotBlank() && request.question.length <= MAX_QUESTION_LENGTH) { "Question is invalid" }
                val path = sanitizePath(request.repoPath) ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("Invalid or unsafe repository path"))
                val config = ConfigLoader.loadForRepository(path)
                if (!config.ai.enabled) return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError("AI disabled in config"))
                val (graph, parsedFiles, _) = AnalysisLogic.analyze(path, config)
                val context = CodebaseContext(parsedFiles.size, listOf("Kotlin/Java"), graph.getTopHotspots(10).map { it.first }, emptyList())
                call.respond(AICodeAnalyzer(config.ai.apiKey, config.ai.model, config.ai.provider).askQuestion(request.question, context))
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                System.err.println("AI request failed: ${e::class.simpleName}")
                call.respond(io.ktor.http.HttpStatusCode.BadGateway, ApiError("AI provider request failed"))
            }
        }

        post("/analyze-org") {
            try {
                val paths = call.receive<List<String>>()
                require(paths.isNotEmpty() && paths.size <= 20) { "At most 20 repositories may be analyzed per request" }
                paths.forEach { require(sanitizePath(it) != null) { "Invalid or unsafe repository path" } }
                call.respond(com.vericore.enterprise.OrganizationAnalyzer().analyzeRepositories(paths))
            } catch (e: IllegalArgumentException) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request"))
            } catch (e: Exception) {
                call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError("Organization analysis failed"))
            }
        }
    }
}

fun validateRevisionPair(baseRevision: String?, headRevision: String?) {
    require((baseRevision == null) == (headRevision == null)) { "baseRevision and headRevision must be supplied together" }
    listOfNotNull(baseRevision, headRevision).forEach { revision -> require(revision.isNotBlank() && revision.length <= MAX_REVISION_LENGTH) { "Git revision is invalid" } }
}

object AnalysisLogic {
    suspend fun analyze(repoPath: String, config: VericoreConfig = ConfigLoader.loadForRepository(repoPath)): Triple<RobustDependencyGraph, List<com.vericore.core.parser.ParsedFile>, CacheManager> {
        val files = RepositoryScanner(config).scan(repoPath)
        require(files.size <= config.maxFilesAnalyze) { "Repository exceeds the maximum file limit: ${config.maxFilesAnalyze}" }
        val cacheManager = CacheManager()
        val parsedFiles = CodeParallelParser(cacheManager).parseFiles(files)
        val graph = RobustDependencyGraph()
        graph.build(parsedFiles)
        graph.analyze()
        return Triple(graph, parsedFiles, cacheManager)
    }
}

fun sanitizePath(inputPath: String): String? {
    return try {
        if (inputPath.isBlank() || inputPath.length > 4096) return null
        val candidate = Paths.get(inputPath).toRealPath()
        if (!Files.isDirectory(candidate) || !Files.isReadable(candidate)) return null
        val configured = System.getenv("VERICORE_ALLOWED_PATHS") ?: System.getenv("CODECONTEXT_ALLOWED_PATHS")
        val rootInputs = configured?.split(File.pathSeparator)?.filter { it.isNotBlank() }
            ?: listOf(System.getProperty("user.dir"), System.getProperty("java.io.tmpdir"))
        val roots = resolveSafeAllowedRoots(rootInputs)
        if (roots.any { root -> candidate == root || candidate.startsWith(root) }) candidate.toString() else null
    } catch (_: Exception) { null }
}


/**
 * Resolve configured repository roots while refusing filesystem roots. A server started
 * with "/" (or a drive root on Windows) as its working directory must not implicitly
 * grant access to the entire machine.
 */
internal fun resolveSafeAllowedRoots(rootInputs: List<String>): List<Path> =
    rootInputs.mapNotNull { runCatching { Paths.get(it).toRealPath() }.getOrNull() }
        .filterNot { it.parent == null }

fun Application.configureRateLimiting() {
    val config = ConfigLoader.load()
    if (!config.rateLimit.enabled) return
    val limiter = com.vericore.server.RateLimiter(config.rateLimit.requestsPerMinute, config.rateLimit.requestsPerHour)
    intercept(ApplicationCallPipeline.Call) {
        val clientId = call.request.header("x-api-key")?.let { "key:${it.hashCode()}" } ?: "ip:${call.request.local.remoteHost}"
        if (!limiter.checkLimit(clientId)) {
            val retryAfter = limiter.getSecondsUntilReset(clientId)
            call.response.headers.append("Retry-After", retryAfter.toString())
            call.respond(io.ktor.http.HttpStatusCode.TooManyRequests, ApiError("Rate limit exceeded"))
            return@intercept finish()
        }
        call.response.headers.append("X-RateLimit-Limit", config.rateLimit.requestsPerMinute.toString())
        call.response.headers.append("X-RateLimit-Remaining", limiter.getRemainingMinute(clientId).toString())
        proceed()
    }
}
