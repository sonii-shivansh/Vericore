package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.EngineeringContextDiff
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.intelligence.EngineeringContextSnapshot
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

class EngineeringContextSnapshotCommand : CliktCommand(name = "context-snapshot", help = "Create a deterministic repository engineering-context snapshot") {
    private val path by argument("path", help = "Repository path")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable snapshot JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save snapshot JSON artifact")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }
        val snapshot = EngineeringContextEngine.snapshot(
            root,
            com.vericore.core.scanner.RepositoryScanner(ConfigLoader.loadForRepository(root.path))
        )
        val defaultOutput = snapshotOutputPath(root)
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(EngineeringContextEngine.encode(snapshot))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "context-snapshot",
                status = CommandStatus.PASS,
                summary = "Engineering context snapshot: ${snapshot.totalFiles} files, ${snapshot.totalBytes} bytes, dirty=${snapshot.dirty}, digest=${snapshot.snapshotDigest.take(12)}",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = snapshot,
                artifacts = listOf(
                    EnvelopeArtifact("context-snapshot", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        if (output != null) {
            val dest = File(output!!).let { if (it.isAbsolute) it else File(path).canonicalFile.resolve(it.path) }
            dest.parentFile?.mkdirs()
            dest.writeText(EngineeringContextEngine.encode(snapshot))
        }

        echo(renderEngineeringContextSnapshotSummary(snapshot, defaultOutput.absolutePath))
    }
}

class EngineeringContextDiffCommand : CliktCommand(name = "context-diff", help = "Compare two engineering-context snapshots") {
    private val before by argument("before", help = "Baseline snapshot JSON")
    private val after by argument("after", help = "Current snapshot JSON")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable diff JSON to stdout (alias for --format json)").flag()
    private val output by option("--output", help = "Optional path to save diff JSON artifact")

    override fun run() {
        val json = Json { ignoreUnknownKeys = false }
        val baseline = json.decodeFromString(EngineeringContextSnapshot.serializer(), File(before).readText())
        val current = json.decodeFromString(EngineeringContextSnapshot.serializer(), File(after).readText())
        val diff = EngineeringContextEngine.diff(baseline, current)

        val defaultOutput = diffOutputPath()
        defaultOutput.parentFile.mkdirs()
        defaultOutput.writeText(EngineeringContextEngine.encode(diff))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        val hasChanges = diff.summary.added > 0 || diff.summary.removed > 0 || diff.summary.modified > 0
        val status = if (hasChanges) CommandStatus.WARNING else CommandStatus.PASS

        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "context-diff",
                status = status,
                summary = "Context diff: +${diff.summary.added} -${diff.summary.removed} ~${diff.summary.modified} =${diff.summary.unchanged}",
                data = diff,
                findings = diff.changes.map { c ->
                    EnvelopeFinding(
                        level = if (c.type == "MODIFIED") "WARNING" else "INFO",
                        message = "${c.type}: ${c.path}",
                        path = c.path
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("context-diff", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (output != null) {
                val dest = File(output!!)
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        if (output != null) {
            val dest = File(output!!)
            dest.parentFile?.mkdirs()
            dest.writeText(EngineeringContextEngine.encode(diff))
        }

        echo(renderEngineeringContextDiffSummary(diff, defaultOutput.absolutePath))
    }
}

internal fun renderEngineeringContextSnapshotSummary(
    snapshot: EngineeringContextSnapshot,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Engineering context: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Commit: ${snapshot.repositoryCommit ?: "unknown"}",
                "Files: ${snapshot.totalFiles}",
                "Bytes: ${snapshot.totalBytes}",
                "Dirty: ${snapshot.dirty}",
                "Digest: ${snapshot.snapshotDigest}"
            )
        )
    }
    return HumanCommandOutput.render(title = "🧭 Engineering Context", sections = sections)
}

private fun snapshotOutputPath(root: File): File = root.resolve("output/engineering-context.json")

internal fun renderEngineeringContextDiffSummary(
    diff: EngineeringContextDiff,
    outputPath: String? = null
): String {
    val sections = buildList {
        outputPath?.let { add(HumanCommandOutput.section("Report", "Context diff: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Added: ${diff.summary.added}",
                "Removed: ${diff.summary.removed}",
                "Modified: ${diff.summary.modified}",
                "Unchanged: ${diff.summary.unchanged}"
            )
        )
        diff.changes.take(30).takeIf { it.isNotEmpty() }?.let { changes ->
            add(HumanCommandOutput.section("Changes", *changes.map { "${it.type}: ${it.path}" }.toTypedArray()))
        }
    }
    return HumanCommandOutput.render(
        title = "🧭 Engineering Context Diff",
        sections = sections
    )
}

private fun diffOutputPath(): File = File("output/engineering-context-diff.json")
