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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.measureTimeMillis

class ImprovedAnalyzeCommand : CliktCommand(
    name = "analyze",
    help = "Analyze a repository and generate engineering intelligence"
) {
    private val path by argument("path", help = "Repository path")
    private val noCache by option("--no-cache").flag()
    private val clearCache by option("--clear-cache").flag()
    private val noSnapshot by option("--no-snapshot").flag()
    private val verbose by option("--verbose", "-v").flag()

    override fun run() {
        echo("🚀 Starting Vericore analysis for: $path")
        val rootDir = File(path).canonicalFile
        require(rootDir.exists()) { "Path does not exist: $path" }
        require(rootDir.isDirectory) { "Path is not a directory: $path" }

        val config = ConfigLoader.loadForRepository(rootDir.path)
        val session = SessionRecorder.start(rootDir, "analyze", listOf(path, "--no-cache=$noCache", "--clear-cache=$clearCache", "--no-snapshot=$noSnapshot"))
        val time = measureTimeMillis {
            try {
                if (clearCache) {
                    CacheManager().clear()
                    echo("🗑️  Cache cleared")
                }

                session.event("scan", "Scanning repository")
                echo("📂 Scanning repository...")
                val scanner = RepositoryScanner()
                val files = scanner.scan(rootDir.path)
                session.event("scan-complete", "Found ${files.size} source files")
                echo("   Found ${files.size} files")

                require(files.isNotEmpty()) {
                    "No source files found. Supported extensions: .kt, .java"
                }
                require(files.size <= config.maxFilesAnalyze) {
                    "Too many files (${files.size}). Limit: ${config.maxFilesAnalyze}"
                }

                session.event("parse", "Parsing source files")
                echo("🧠 Parsing code...")
                val cacheManager = if (config.enableCache && !noCache) CacheManager() else null
                val parser = CodeParallelParser(cacheManager)
                val parsedFiles: List<ParsedFile> = try {
                    runBlocking { parser.parseFiles(files) }
                } catch (e: Exception) {
                    if (verbose) println(e.stackTraceToString())
                    throw IllegalStateException("Parsing failed: ${e.message}", e)
                }
                echo("   Parsed ${parsedFiles.size} files")
                val failedCount = parser.lastWarningCount
                if (failedCount > 0) echo("   ⚠️  $failedCount files reported parser diagnostics")

                session.event("git-history", "Analyzing Git history")
                echo("📜 Analyzing Git history...")
                val enrichedFiles = try {
                    OptimizedGitAnalyzer().analyze(rootDir.path, parsedFiles)
                } catch (e: Exception) {
                    echo("   ⚠️  Git analysis failed: ${e.message}")
                    if (verbose) println(e.stackTraceToString())
                    parsedFiles
                }

                session.event("dependency-graph", "Building dependency graph")
                echo("🕸️  Building dependency graph...")
                val graph = RobustDependencyGraph()
                val buildResult = graph.build(enrichedFiles)
                if (buildResult.isFailure) {
                    val cause = buildResult.exceptionOrNull()
                    if (verbose) cause?.let { println(it.stackTraceToString()) }
                    throw IllegalStateException("Failed to build graph: ${cause?.message}", cause)
                }
                val analyzeResult = graph.analyze()
                if (analyzeResult.isFailure) {
                    val cause = analyzeResult.exceptionOrNull()
                    if (verbose) cause?.let { println(it.stackTraceToString()) }
                    throw IllegalStateException("Failed to analyze graph: ${cause?.message}", cause)
                }

                val hotspots = graph.getTopHotspots(config.hotspotCount)
                echo("🗺️  Your Codebase Map")
                echo("├─ 🔥 Hot Zones (Top ${minOf(5, hotspots.size)}):")
                hotspots.take(5).forEachIndexed { index, (file, score) ->
                    val prefix = if (index == 4 || index == hotspots.lastIndex) "│   └─" else "│   ├─"
                    echo("$prefix ${File(file).name} (${String.format("%.4f", score)})")
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
                echo("🛡️  Engineering risk: $highRiskCount high/critical files")

                val outputDir = rootDir.resolve("output")
                if (!outputDir.exists()) outputDir.mkdirs()
                if (!noSnapshot) {
                    val snapshotFile = File(outputDir, "analysis-snapshot.json")
                    snapshotFile.writeText(Json { prettyPrint = true }.encodeToString(snapshot))
                    session.event("artifact", "Analysis snapshot written", rootDir.toPath().relativize(snapshotFile.toPath()).toString())
                    echo("🧾 Analysis snapshot: ${snapshotFile.absolutePath}")

                    val riskFile = File(outputDir, "engineering-risks.json")
                    riskFile.writeText(Json { prettyPrint = true }.encodeToString(risks))
                    echo("🛡️  Risk report: ${riskFile.absolutePath}")
                }

                session.event("report", "Generating analysis report")
                echo("📊 Generating report...")
                val reportFile = File(outputDir, "index.html")
                val learningPath = com.vericore.core.generator.LearningPathGenerator().generate(graph)
                ReportGenerator().generate(graph, reportFile.absolutePath, enrichedFiles, learningPath)
                session.event("artifact", "HTML report written", rootDir.toPath().relativize(reportFile.toPath()).toString())
                echo("✅ Report: ${reportFile.absolutePath}")

                if (config.ai.enabled && config.ai.apiKey.isNotBlank()) {
                    echo("🤖 Generating AI Insights...")
                    val aiAnalyzer = AICodeAnalyzer(config.ai.apiKey, config.ai.model, config.ai.provider)
                    if (!aiAnalyzer.isConfigured()) {
                        echo("   ⚠️  AI is enabled but not properly configured")
                    } else {
                        try {
                            runBlocking {
                                val insights = aiAnalyzer.batchAnalyze(enrichedFiles, graph, limit = 10)
                                val aiReportFile = File(outputDir, "ai-insights.md")
                                aiReportFile.writeText("# AI Code Insights\n\n")
                                insights.forEach { (insightPath, insight) ->
                                    aiReportFile.appendText("## ${File(insightPath).name}\n")
                                    aiReportFile.appendText("**Purpose**: ${insight.purpose}\n\n")
                                    aiReportFile.appendText("**Complexity**: ${insight.complexity}/10\n")
                                    aiReportFile.appendText("**Refactoring Tips**: ${insight.refactoringTips.joinToString(", ")}\n\n")
                                }
                                echo("✨ AI Insights saved to: ${aiReportFile.absolutePath}")
                            }
                        } catch (e: Exception) {
                            echo("   ⚠️ AI insights generation failed: ${e.message}")
                            if (verbose) println(e.stackTraceToString())
                        }
                    }
                }
                session.complete(
                    status = "COMPLETED",
                    summary = "Analysis completed in ${time}ms",
                    snapshotPath = if (!noSnapshot) rootDir.toPath().relativize(rootDir.resolve("output/analysis-snapshot.json").toPath()).toString() else null,
                    reportPath = rootDir.toPath().relativize(rootDir.resolve("output/index.html").toPath()).toString()
                )
            } catch (e: Exception) {
                session.complete(status = "FAILED", summary = e.message ?: e::class.simpleName.orEmpty())
                if (e is IllegalStateException || e is IllegalArgumentException) throw e
                echo("❌ Analysis failed: ${e.message}")
                if (verbose) println(e.stackTraceToString())
                throw e
            }
        }
        echo("✨ Complete in ${time}ms")
    }
}
