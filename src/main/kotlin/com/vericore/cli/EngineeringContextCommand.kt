package com.vericore.cli

import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.intelligence.EngineeringContextSnapshot
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.json.Json

class EngineeringContextSnapshotCommand : CliktCommand(name = "context-snapshot", help = "Create a deterministic repository engineering-context snapshot") {
    private val path by argument("path", help = "Repository path")
    private val jsonOutput by option("--json", help = "Write machine-readable snapshot JSON").flag()

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }
        val snapshot = EngineeringContextEngine.snapshot(
            root,
            com.vericore.core.scanner.RepositoryScanner(ConfigLoader.loadForRepository(root.path))
        )
        val output = snapshotOutputPath(root)
        if (jsonOutput) {
            output.parentFile.mkdirs()
            output.writeText(EngineeringContextEngine.encode(snapshot))
        }
        echo(renderEngineeringContextSnapshotSummary(snapshot, if (jsonOutput) output.absolutePath else null))
    }
}

class EngineeringContextDiffCommand : CliktCommand(name = "context-diff", help = "Compare two engineering-context snapshots") {
    private val before by argument("before", help = "Baseline snapshot JSON")
    private val after by argument("after", help = "Current snapshot JSON")
    private val jsonOutput by option("--json", help = "Write machine-readable diff JSON").flag()

    override fun run() {
        val json = Json { ignoreUnknownKeys = false }
        val baseline = json.decodeFromString(EngineeringContextSnapshot.serializer(), File(before).readText())
        val current = json.decodeFromString(EngineeringContextSnapshot.serializer(), File(after).readText())
        val diff = EngineeringContextEngine.diff(baseline, current)
        if (jsonOutput) {
            val output = diffOutputPath()
            output.parentFile.mkdirs()
            output.writeText(EngineeringContextEngine.encode(diff))
        }
        echo(renderEngineeringContextDiffSummary(diff, if (jsonOutput) diffOutputPath().absolutePath else null))
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
    diff: com.vericore.core.intelligence.EngineeringContextDiff,
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
    return HumanCommandOutput.render(title = "🧭 Engineering Context Diff", sections = sections)
}

private fun diffOutputPath(): File = File("output/engineering-context-diff.json")
