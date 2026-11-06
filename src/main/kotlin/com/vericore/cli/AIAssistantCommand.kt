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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class AIAssistantCommand :
    CliktCommand(name = "ask") {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Ask questions about your codebase using AI"
    private val question by argument("question", help = "The question to ask").default("")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable AI response as JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save AI response JSON artifact")

    override fun run() {
        if (question.isBlank()) {
            throw com.github.ajalt.clikt.core.PrintHelpMessage(currentContext)
        }

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val isJson = outFormat == OutputFormat.JSON

        var config = ConfigLoader.loadEffective()
        if (!config.ai.enabled || config.ai.apiKey.isBlank()) {
            if (!AISetupPrompter.ensureConfigured(config.ai.model)) return
            config = ConfigLoader.loadEffective()
        }

        if (!isJson) {
            echo("🤖 Analyzing codebase to answer: \"$question\"")
        }

        runBlocking {
            if (!isJson) echo("   Gathering context...")
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

                if (isJson) {
                    val envelope = CommandEnvelope(
                        schemaVersion = "1.0",
                        command = "ask",
                        status = CommandStatus.SUCCESS,
                        summary = "AI answer for: \"$question\"",
                        repository = RepositoryInfo(name = root.name, path = root.absolutePath),
                        data = response,
                        findings = response.suggestedFiles.map { f ->
                            EnvelopeFinding(
                                level = "INFO",
                                message = "Suggested relevant file",
                                path = f
                            )
                        },
                        execution = ExecutionInfo(exitCode = 0)
                    )
                    val jsonText = JsonEnvelope.encode(envelope)
                    if (output != null) {
                        val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                        dest.parentFile?.mkdirs()
                        dest.writeText(jsonText)
                    }
                    echo(jsonText)
                    return@runBlocking
                }

                if (output != null) {
                    val dest = File(output!!).let { if (it.isAbsolute) it else root.resolve(it.path) }
                    dest.parentFile?.mkdirs()
                    dest.writeText(JsonEnvelope.json.encodeToString(response))
                }

                echo(renderAskSummary(response.answer, response.suggestedFiles, response.confidence))
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

/** Formats the final answer only; progress output and provider/error behavior remain unchanged. */
internal fun renderAskSummary(answer: String, suggestedFiles: List<String>, confidence: Double): String =
    HumanCommandOutput.render(
        title = "💡 Answer",
        sections = buildList {
            add(HumanCommandOutput.section("Response", answer))
            if (suggestedFiles.isNotEmpty()) add(HumanCommandOutput.section("Check these files", *suggestedFiles.toTypedArray()))
            add(HumanCommandOutput.section("Confidence", "${(confidence * 100).toInt()}%"))
        }
    )
