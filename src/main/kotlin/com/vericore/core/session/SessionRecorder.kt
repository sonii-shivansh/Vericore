package com.vericore.core.session

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SessionRecord(
    val schemaVersion: String = "1.0",
    val id: String,
    val command: String,
    val repositoryPath: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val status: String = "RUNNING",
    val snapshotPath: String? = null,
    val reportPath: String? = null,
    val summary: String? = null
)

@Serializable
data class SessionEvent(
    val timestampEpochMillis: Long,
    val type: String,
    val message: String,
    val artifact: String? = null
)

class SessionRecorder private constructor(
    private val sessionDir: File,
    private var record: SessionRecord
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val eventsFile = File(sessionDir, "commands.jsonl")
    private val sessionFile = File(sessionDir, "session.json")

    init { persist() }

    fun event(type: String, message: String, artifact: String? = null) {
        eventsFile.appendText(
            Json.encodeToString(SessionEvent(System.currentTimeMillis(), type, message, artifact)) + System.lineSeparator()
        )
    }

    fun complete(status: String, summary: String, snapshotPath: String? = null, reportPath: String? = null) {
        record = record.copy(
            completedAtEpochMillis = System.currentTimeMillis(),
            status = status,
            snapshotPath = snapshotPath,
            reportPath = reportPath,
            summary = summary
        )
        persist()
        event("completed", summary, reportPath ?: snapshotPath)
    }

    private fun persist() { sessionFile.writeText(json.encodeToString(record)) }

    companion object {
        fun start(repositoryRoot: File, command: String, args: List<String>): SessionRecorder {
            val workspace = repositoryRoot.resolve(".vericore").resolve("sessions")
            require(workspace.mkdirs() || workspace.isDirectory) {
                "Unable to create Vericore session directory: " + workspace.absolutePath
            }
            val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
            val sessionDir = workspace.resolve(id)
            require(sessionDir.mkdirs()) { "Unable to create Vericore session: " + sessionDir.absolutePath }
            val recorder = SessionRecorder(
                sessionDir,
                SessionRecord(id = id, command = command, repositoryPath = repositoryRoot.absolutePath, startedAtEpochMillis = System.currentTimeMillis())
            )
            val safeArgs = args.filterNot { it.contains("api", ignoreCase = true) && it.contains("key", ignoreCase = true) }
            recorder.event("started", (command + " " + safeArgs.joinToString(" ")).trim())
            return recorder
        }
    }
}