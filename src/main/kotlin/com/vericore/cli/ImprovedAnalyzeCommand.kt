package com.vericore.cli

import com.vericore.core.ai.AICodeAnalyzer
import com.vericore.core.cache.CacheManager
import com.vericore.core.config.ConfigLoader
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.intelligence.AnalysisSnapshotBuilder
import com.vericore.core.intelligence.EngineeringRiskEngine
import com.vericore.core.parser.ParsedFile
import com.vericore.core.scanner.OptimizedGitAnalyzer
import com.vericore.core.scanner.RepositoryScanner
import com.vericore.core.session.SessionRecorder
import com.vericore.output.ReportGenerator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.measureTimeMillis

class ImprovedAnalyzeCommand(
    private val commandIdentity: String = "analyze"
) : CliktCommand(
    name = commandIdentity
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Analyze a repository and generate engineering intelligence"
    private val path by argument("path", help = "Repository path")
    private val noCache by option("--no-cache").flag()
    private val clearCache by option("--clear-cache").flag()
    private val noSnapshot by option("--no-snapshot").flag()
    private val verbose by option("--verbose", "-v").flag()
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable analysis JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save analysis JSON output")

    override fun run() {
        runAnalysisEngine(
            commandIdentity = commandIdentity,
            path = path,
            noCache = noCache,
            clearCache = clearCache,
            noSnapshot = noSnapshot,
            verbose = verbose,
            format = format,
            jsonOutput = jsonOutput,
            output = output,
            echo = { echo(it) }
        )
    }
}

internal fun runAnalysisEngine(
    commandIdentity: String,
    path: String,
    noCache: Boolean,
    clearCache: Boolean,
    noSnapshot: Boolean,
    verbose: Boolean,
    format: String?,
    jsonOutput: Boolean,
    output: String?,
    echo: (String) -> Unit
) {
    val outFormat = OutputFormat.resolve(format, jsonOutput)
    val isJson = outFormat == OutputFormat.JSON

    fun log(msg: String) {
        if (isJson) {
            if (verbose) System.err.println(msg)
        } else {
            echo(msg)
        }
    }

        log("🚀 Starting Vericore analysis for: $path")
        val rootDir = File(path).canonicalFile
        require(rootDir.exists()) { "Path does not exist: $path" }
        require(rootDir.isDirectory) { "Path is not a directory: $path" }

        val config = ConfigLoader.loadForRepository(rootDir.path)
        val session = SessionRecorder.start(rootDir, "analyze", listOf(path, "--no-cache=$noCache", "--clear-cache=$clearCache", "--no-snapshot=$noSnapshot"))
        val startTime = System.currentTimeMillis()
        try {
                if (clearCache) {
                    CacheManager().clear()
                    log("🗑️  Cache cleared")
                }

                session.event("scan", "Scanning repository")
                log("📂 Scanning repository...")
                val scanner = RepositoryScanner()
                val files = scanner.scan(rootDir.path)
                session.event("scan-complete", "Found ${files.size} source files")
                log("   Found ${files.size} files")

                require(files.isNotEmpty()) {
                    "No source files found. Supported extensions: .kt, .java"
                }
                require(files.size <= config.maxFilesAnalyze) {
                    "Too many files (${files.size}). Limit: ${config.maxFilesAnalyze}"
                }

                session.event("parse", "Parsing source files")
                log("🧠 Parsing code...")
                val cacheManager = if (config.enableCache && !noCache) CacheManager() else null
                val parser = CodeParallelParser(cacheManager)
                val parsedFiles: List<ParsedFile> = try {
                    runBlocking { parser.parseFiles(files) }
                } catch (e: Exception) {
                    if (verbose) System.err.println(e.stackTraceToString())
                    throw IllegalStateException("Parsing failed: ${e.message}", e)
                }
                log("   Parsed ${parsedFiles.size} files")
                val failedCount = parser.lastWarningCount
                if (failedCount > 0) log("   ⚠️  $failedCount files reported parser diagnostics")

                session.event("git-history", "Analyzing Git history")
                log("📜 Analyzing Git history...")
                val enrichedFiles = try {
                    OptimizedGitAnalyzer().analyze(rootDir.path, parsedFiles)
                } catch (e: Exception) {
                    log("   ⚠️  Git analysis failed: ${e.message}")
                    if (verbose) System.err.println(e.stackTraceToString())
                    parsedFiles
                }

                session.event("dependency-graph", "Building dependency graph")
                log("🕸️  Building dependency graph...")
                val graph = RobustDependencyGraph()
                val buildResult = graph.build(enrichedFiles)
                if (buildResult.isFailure) {
                    val cause = buildResult.exceptionOrNull()
                    if (verbose) cause?.let { System.err.println(it.stackTraceToString()) }
                    throw IllegalStateException("Failed to build graph: ${cause?.message}", cause)
                }
                val analyzeResult = graph.analyze()
                if (analyzeResult.isFailure) {
                    val cause = analyzeResult.exceptionOrNull()
                    if (verbose) cause?.let { System.err.println(it.stackTraceToString()) }
                    throw IllegalStateException("Failed to analyze graph: ${cause?.message}", cause)
                }

                val hotspots = graph.getTopHotspots(config.hotspotCount)
                if (!isJson) {
                    echo("🗺️  Your Codebase Map")
                    echo("├─ 🔥 Hot Zones (Top ${minOf(5, hotspots.size)}):")
                    hotspots.take(5).forEachIndexed { index, (file, score) ->
                        val prefix = if (index == 4 || index == hotspots.lastIndex) "│   └─" else "│   ├─"
                        echo("$prefix ${File(file).name} (${String.format("%.4f", score)})")
                    }
                }

                val snapshot = AnalysisSnapshotBuilder.build(
                    repositoryPath = rootDir.path,
                    parsedFiles = enrichedFiles,
                    graph = graph.graph,
                    pageRankScores = graph.pageRankScores,
                    hasCycles = graph.hasCycles,
                    parseFailures = failedCount
                )
                val risks = EngineeringRiskEngine.calculate(snapshot)
                val highRiskCount = risks.count { it.level.name == "HIGH" || it.level.name == "CRITICAL" }
                log("🛡️  Engineering risk: $highRiskCount high/critical files")

                val outputDir = rootDir.resolve("output")
                if (!outputDir.exists()) outputDir.mkdirs()
                val snapshotFile = File(outputDir, "analysis-snapshot.json")
                val riskFile = File(outputDir, "engineering-risks.json")
                if (!noSnapshot) {
                    snapshotFile.writeText(JsonEnvelope.json.encodeToString(snapshot))
                    session.event("artifact", "Analysis snapshot written", rootDir.toPath().relativize(snapshotFile.toPath()).toString())
                    log("🧾 Analysis snapshot: ${snapshotFile.absolutePath}")

                    riskFile.writeText(JsonEnvelope.json.encodeToString(risks))
                    log("🛡️  Risk report: ${riskFile.absolutePath}")
                }

                session.event("report", "Generating analysis report")
                log("📊 Generating report...")
                val reportFile = File(outputDir, "index.html")
                val learningPath = com.vericore.core.generator.LearningPathGenerator().generate(graph)
                ReportGenerator().generate(graph, reportFile.absolutePath, enrichedFiles, learningPath)
                session.event("artifact", "HTML report written", rootDir.toPath().relativize(reportFile.toPath()).toString())
                log("✅ Report: ${reportFile.absolutePath}")

                if (config.ai.enabled && config.ai.apiKey.isNotBlank()) {
                    log("🤖 Generating AI Insights...")
                    val aiAnalyzer = AICodeAnalyzer(config.ai.apiKey, config.ai.model, config.ai.provider)
                    if (!aiAnalyzer.isConfigured()) {
                        log("   ⚠️  AI is enabled but not properly configured")
                    } else {
                        try {
                            runBlocking {
                                val insights = aiAnalyzer.batchAnalyze(enrichedFiles, graph, limit = 10)
                                val aiReportFile = File(outputDir, "ai-insights.md")
                                aiReportFile.writeText("# AI Code Insights" + System.lineSeparator() + System.lineSeparator())
                                insights.forEach { (insightPath, insight) ->
                                    aiReportFile.appendText("## ${File(insightPath).name}" + System.lineSeparator())
                                    aiReportFile.appendText("**Purpose**: ${insight.purpose}" + System.lineSeparator() + System.lineSeparator())
                                    aiReportFile.appendText("**Complexity**: ${insight.complexity}/10" + System.lineSeparator())
                                    aiReportFile.appendText("**Refactoring Tips**: ${insight.refactoringTips.joinToString(", ")}" + System.lineSeparator() + System.lineSeparator())
                                }
                                log("✨ AI Insights saved to: ${aiReportFile.absolutePath}")
                            }
                        } catch (e: Exception) {
                            log("   ⚠️ AI insights generation failed: ${e.message}")
                            if (verbose) System.err.println(e.stackTraceToString())
                        }
                    }
                }
                session.complete(
                    status = "COMPLETED",
                    summary = "Analysis completed successfully",
                    snapshotPath = if (!noSnapshot) rootDir.toPath().relativize(snapshotFile.toPath()).toString() else null,
                    reportPath = rootDir.toPath().relativize(reportFile.toPath()).toString()
                )

                val elapsedMs = System.currentTimeMillis() - startTime
                if (isJson) {
                    val envelope = CommandEnvelope(
                        schemaVersion = "1.0",
                        command = commandIdentity,
                        status = CommandStatus.SUCCESS,
                        summary = "Analyzed ${enrichedFiles.size} source files in ${elapsedMs}ms: ${highRiskCount} high/critical risk files, ${graph.graph.vertexSet().size} graph nodes, ${graph.graph.edgeSet().size} dependency edges",
                        repository = RepositoryInfo(name = rootDir.name, path = rootDir.path),
                        data = snapshot,
                        findings = risks.filter { it.level.name == "HIGH" || it.level.name == "CRITICAL" }.map { r ->
                            EnvelopeFinding(
                                level = r.level.name,
                                message = "Engineering risk score ${String.format("%.2f", r.score)}: ${r.reasons.joinToString("; ")}",
                                path = repositoryRelativePath(r.path, rootDir)
                            )
                        },
                        artifacts = buildList {
                            if (!noSnapshot) {
                                add(EnvelopeArtifact("analysis-snapshot", snapshotFile.path))
                                add(EnvelopeArtifact("risk-report", riskFile.path))
                            }
                            add(EnvelopeArtifact("html-report", reportFile.path))
                        },
                        nextStep = "vericore inspect --path ${rootDir.path}",
                        execution = ExecutionInfo(exitCode = 0)
                    )
                    val encodedEnvelope = JsonEnvelope.encode(envelope)
                    if (output != null) {
                        val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
                        dest.parentFile?.mkdirs()
                        dest.writeText(encodedEnvelope)
                    }
                    echo(encodedEnvelope)
                    return
                }

                if (output != null) {
                    val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
                    dest.parentFile?.mkdirs()
                    dest.writeText(JsonEnvelope.json.encodeToString(snapshot))
                }

                echo("✨ Complete in ${elapsedMs}ms")
            } catch (e: Exception) {
                session.complete(status = "FAILED", summary = e.message ?: e::class.simpleName.orEmpty())
                if (e is IllegalStateException || e is IllegalArgumentException) throw e
                System.err.println("❌ Analysis failed: ${e.message}")
                if (verbose) System.err.println(e.stackTraceToString())
                throw e
            }
    }
