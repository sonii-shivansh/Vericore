package com.vericore.core.workflow

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable

@Serializable
data class VerificationCommandResult(
    val command: String,
    val executed: Boolean,
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
    val durationMillis: Long = 0,
    val stdout: String = "",
    val stderr: String = "",
    val error: String? = null
)

object VerificationCommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS = 600L
    private const val MAX_CAPTURED_OUTPUT = 16_384

    fun execute(repository: File, commands: List<String>, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): List<VerificationCommandResult> {
        if (commands.isEmpty()) return emptyList()
        require(timeoutSeconds > 0) { "Verification command timeout must be positive." }
        return commands.map { command -> executeOne(repository, command, timeoutSeconds) }
    }

    private fun executeOne(repository: File, command: String, timeoutSeconds: Long): VerificationCommandResult {
        val started = System.nanoTime()
        if (!isSafeRepositoryBuildCommand(repository, command)) {
            return VerificationCommandResult(
                command = command,
                executed = false,
                durationMillis = elapsedMillis(started),
                error = "Verification command is not an allowed repository build/test command."
            )
        }

        return try {
            val process = ProcessBuilder(shell(), shellArgument(), command)
                .directory(repository)
                .redirectErrorStream(false)
                .start()
            val stdoutThread = Thread { readAndStore(process.inputStream) }
            val stderrThread = Thread { readAndStore(process.errorStream) }
            stdoutThread.start()
            stderrThread.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            stdoutThread.join(5_000)
            stderrThread.join(5_000)
            VerificationCommandResult(
                command = command,
                executed = true,
                exitCode = if (finished) process.exitValue() else null,
                timedOut = !finished,
                durationMillis = elapsedMillis(started),
                stdout = captured(stdoutThread),
                stderr = captured(stderrThread),
                error = if (finished) null else "Verification command timed out after ${timeoutSeconds}s."
            )
        } catch (e: Exception) {
            VerificationCommandResult(
                command = command,
                executed = false,
                durationMillis = elapsedMillis(started),
                error = e.message ?: e::class.java.simpleName
            )
        }
    }

    private fun isSafeRepositoryBuildCommand(repository: File, command: String): Boolean {
        val normalized = command.trim()
        val root = repository.canonicalPath.replace("'", "'\\''")
        val prefix = "cd '$root' && "
        if (!normalized.startsWith(prefix)) return false
        val actual = normalized.removePrefix(prefix).trim()
        if (actual.isEmpty()) return false
        if (actual.contains(';') || actual.contains("&&") || actual.contains("||") || actual.contains('`') || actual.contains("${'$'}(") || actual.contains('>')) return false
        return actual == "./mvnw" || actual.startsWith("./mvnw ") ||
            actual == "./gradlew" || actual.startsWith("./gradlew ") ||
            actual == "mvn" || actual.startsWith("mvn ") ||
            actual == "gradle" || actual.startsWith("gradle ")
    }

    private fun shell(): String = if (isWindows()) "cmd" else "sh"
    private fun shellArgument(): String = if (isWindows()) "/c" else "-lc"
    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun readAndStore(stream: java.io.InputStream) {
        stream.use { input ->
            val buffer = ByteArray(4096)
            val out = StringBuilder()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.length < MAX_CAPTURED_OUTPUT) {
                    val remaining = MAX_CAPTURED_OUTPUT - out.length
                    out.append(String(buffer, 0, minOf(count, remaining), Charsets.UTF_8))
                }
            }
            capturedByThread[Thread.currentThread()] = out.toString()
        }
    }

    private fun captured(thread: Thread): String = capturedByThread.remove(thread).orEmpty()
    private val capturedByThread = java.util.concurrent.ConcurrentHashMap<Thread, String>()
}
