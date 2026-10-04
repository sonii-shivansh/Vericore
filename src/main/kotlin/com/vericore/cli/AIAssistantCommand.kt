package com.vericore.cli

import com.vericore.core.ai.CodebaseContext
import com.vericore.core.ai.GeminiQuestionService
import com.vericore.core.config.ConfigLoader
import com.vericore.core.exceptions.AIProviderException
import com.vericore.core.exceptions.VericoreException
import com.vericore.core.graph.RobustDependencyGraph
import com.vericore.core.scanner.RepositoryScanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import java.io.File
import kotlinx.coroutines.runBlocking

class AIAssistantCommand :
    CliktCommand(name = "ask", help = "Ask questions about your codebase using AI") {
    private val question by argument("question", help = "The question to ask").default("")

    override fun run() {
        if (question.isBlank()) {
            throw com.github.ajalt.clikt.core.PrintHelpMessage(currentContext)
        }

        var config = ConfigLoader.loadEffective()
        if (!config.ai.enabled || config.ai.apiKey.isBlank()) {
            if (System.console() == null) {
                throw com.vericore.core.exceptions.ConfigurationException(
                    "AI is not configured. Non-interactive 'ask' cannot prompt for credentials. " +
                        "Set VERICORE_GEMINI_API_KEY or run 'vericore setup' in an interactive terminal."
                )
            }
            if (!AISetupPrompter.ensureConfigured(config.ai.model)) {
                throw com.vericore.core.exceptions.ConfigurationException(
                    "AI setup was not completed. Run 'vericore setup' or configure a supported AI credential."
                )
            }
            config = ConfigLoader.loadEffective()
        }

        echo("🤖 Analyzing codebase to answer: \"$question\"")

        runBlocking {
            echo("   Gathering context...")
            val root = File(".")
            val scanner = RepositoryScanner()
            val files = scanner.scan(root.absolutePath)

            val cacheManager = com.vericore.core.cache.CacheManager()
            val parallelParser = CodeParallelParser(cacheManager)
            val parsedFiles: List<com.vericore.core.parser.ParsedFile> = parallelParser.parseFiles(files)

            val graph = RobustDependencyGraph()
            graph.build(parsedFiles)
            graph.analyze()

            val hotspots = graph.getTopHotspots(10).map { it.first }
            val context = CodebaseContext(
                totalFiles = parsedFiles.size,
                languages = listOf("Kotlin/Java"),
                hotspots = hotspots,
                recentChanges = emptyList()
            )

            try {
                val response = if (config.ai.provider.equals("gemini", ignoreCase = true)) {
                    GeminiQuestionService(config.ai.apiKey, config.ai.model).ask(question, context)
                } else {
                    throw IllegalArgumentException("Unsupported AI provider: ${config.ai.provider}")
                }

                echo("\n💡 ${response.answer}\n")
                if (response.suggestedFiles.isNotEmpty()) {
                    echo("📁 Check these files:")
                    response.suggestedFiles.forEach { echo("   - $it") }
                }
                echo("\n🎯 Confidence: ${(response.confidence * 100).toInt()}%")
            } catch (e: Exception) {
                if (e is VericoreException) throw e
                throw AIProviderException(formatProviderFailure(e.message), e)
            }
        }
    }

    private fun formatProviderFailure(message: String?): String {
        val detail = message.orEmpty()
        return when {
            Regex("HTTP\\s+429", RegexOption.IGNORE_CASE).containsMatchIn(detail) ||
                Regex("quota|rate limit|resource exhausted", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                "AI provider quota or rate limit exceeded. Repository analysis completed, but the AI provider could not answer the question. Try again later or configure another supported provider."
            Regex("HTTP\\s+401|HTTP\\s+403", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                "AI provider authentication failed. Check the configured API key and run 'vericore doctor' to validate the setup."
            Regex("unsupported AI provider", RegexOption.IGNORE_CASE).containsMatchIn(detail) ->
                detail
            else ->
                "AI provider request failed. Repository analysis completed, but the AI provider could not answer the question. Check 'vericore doctor' and try again."
        }
    }
}
