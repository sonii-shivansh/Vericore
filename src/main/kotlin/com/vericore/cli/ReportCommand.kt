package com.vericore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.session.SessionRecord
import java.io.File
import kotlinx.serialization.json.Json

class ReportCommand : CliktCommand(name = "report", help = "Generate a durable HTML engineering report from the latest Vericore session") {
    private val path by option("--path", help = "Repository path").default(".")
    private val sessionId by option("--session", help = "Session id; defaults to the latest completed analysis session")
    private val output by option("--output", help = "Output HTML path")

    override fun run() {
        val root = File(path).canonicalFile
        require(root.isDirectory) { "Path is not a directory: " + path }
        val session = resolveSession(root)
        val snapshotPath = session.snapshotPath?.let { File(root, it) } ?: root.resolve("output").resolve("analysis-snapshot.json")
        require(snapshotPath.isFile) { "Analysis snapshot not found: " + snapshotPath.absolutePath + ". Run vericore scan first." }
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<AnalysisSnapshot>(snapshotPath.readText())
        val target = output?.let { File(it).canonicalFile } ?: root.resolve(".vericore").resolve("reports").resolve(session.id + ".html")
        target.parentFile?.mkdirs()
        target.writeText(render(snapshot, session))
        echo("REPORT GENERATED")
        echo("  Session: " + session.id)
        echo("  Repository: " + root.absolutePath)
        echo("  Report: " + target.absolutePath)
    }

    private fun resolveSession(root: File): SessionRecord {
        val sessions = root.resolve(".vericore").resolve("sessions")
        val candidates = sessions.listFiles()?.filter { File(it, "session.json").isFile }.orEmpty()
        val selected = if (sessionId != null) candidates.firstOrNull { it.name == sessionId } else candidates.maxByOrNull { it.lastModified() }
        require(selected != null) { "No Vericore session found. Run vericore scan first." }
        val record = Json { ignoreUnknownKeys = true }.decodeFromString<SessionRecord>(File(selected, "session.json").readText())
        require(record.status == "COMPLETED") { "Session " + record.id + " did not complete successfully." }
        return record
    }

    private fun render(snapshot: AnalysisSnapshot, session: SessionRecord): String {
        fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val hotspots = snapshot.hotspots.take(15).joinToString("") {
            "<tr><td>" + esc(File(it.path).name) + "</td><td>" + "%.4f".format(it.score) + "</td><td>" + it.churn + "</td><td>" + it.dependents + "</td><td>" + it.dependencies + "</td></tr>"
        }
        val duration = session.completedAtEpochMillis?.let { it - session.startedAtEpochMillis } ?: 0L
        return """
<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Vericore Engineering Report</title>
<style>body{font-family:system-ui,sans-serif;background:#f5f7fa;color:#17202a;margin:0;padding:32px}main{max-width:1100px;margin:auto}section{background:#fff;border:1px solid #e1e5ea;border-radius:12px;padding:22px;margin:16px 0}h1{margin-bottom:4px}.muted{color:#667085}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px}.metric{padding:14px;background:#f8fafc;border-radius:8px}.metric b{font-size:24px;display:block}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:9px;border-bottom:1px solid #eee}</style>
</head><body><main>
<h1>Vericore Engineering Report</h1>
<p class="muted">Session ${session.id} · ${snapshot.repository.languages.joinToString(", ").ifBlank { "Unknown" }}</p>
<section><h2>Repository overview</h2><div class="grid">
<div class="metric"><b>${snapshot.metrics.totalFiles}</b>files</div>
<div class="metric"><b>${snapshot.metrics.totalNodes}</b>nodes</div>
<div class="metric"><b>${snapshot.metrics.totalEdges}</b>dependency edges</div>
<div class="metric"><b>${snapshot.metrics.parseFailures}</b>parse diagnostics</div>
</div></section>
<section><h2>Engineering signals</h2><p>Cycles detected: <strong>${snapshot.metrics.cycleDetected}</strong>. Package count: <strong>${snapshot.architecture.packageCount}</strong>. Cross-package edges: <strong>${snapshot.architecture.crossPackageEdges}</strong>.</p>
<p>Session duration: <strong>${duration} ms</strong>.</p></section>
<section><h2>Knowledge hotspots</h2><table><thead><tr><th>File</th><th>Score</th><th>Churn</th><th>Dependents</th><th>Dependencies</th></tr></thead><tbody>$hotspots</tbody></table></section>
<section><h2>Session</h2><p>Status: <strong>${esc(session.status)}</strong></p><p>${esc(session.summary.orEmpty())}</p><p class="muted">Generated from persisted deterministic analysis evidence. This report does not claim AI-generated correctness.</p></section>
</main></body></html>
""".trimIndent()
    }
}