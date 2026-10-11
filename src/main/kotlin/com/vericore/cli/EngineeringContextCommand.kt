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
            val output = File("output/engineering-context-diff.json")
            output.parentFile.mkdirs()
            output.writeText(EngineeringContextEngine.encode(diff))
            echo("🧭 Context diff: ${output.absolutePath}")
        }
        echo("🧭 Engineering Context Diff")
        echo("├─ Added: ${diff.summary.added}")
        echo("├─ Removed: ${diff.summary.removed}")
        echo("├─ Modified: ${diff.summary.modified}")
        echo("└─ Unchanged: ${diff.summary.unchanged}")
        diff.changes.take(30).forEach { change -> echo("   ${change.type}: ${change.path}") }
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
