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
        val validatedCommand = validatedRepositoryBuildCommand(repository, command)
        if (validatedCommand == null) {
            return VerificationCommandResult(
                command = command,
                executed = false,
                durationMillis = elapsedMillis(started),
                error = "Verification command is not an allowed repository build/test command."
            )
        }

        return try {
            // The directory prefix is parsed as metadata and never executed by the shell.
            // ProcessBuilder supplies the already-validated in-repository working directory.
            val process = ProcessBuilder(shell(), shellArgument(), validatedCommand.invocation)
                .directory(validatedCommand.workingDirectory)
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

    private data class ValidatedRepositoryBuildCommand(
        val workingDirectory: File,
        val invocation: String
    )

    /**
     * Parses the persisted directory-prefix + build-command form without executing the
     * directory change. Only an existing directory canonically contained inside the
     * repository is accepted, and the remaining invocation must match a narrow grammar.
     */
    private fun validatedRepositoryBuildCommand(repository: File, command: String): ValidatedRepositoryBuildCommand? {
        if (command.any { it == '\r' || it == '\n' }) return null

        val normalized = command.trim()
        val separatorIndex = normalized.indexOf(" && ")
        if (separatorIndex <= 0) return null

        val directoryCommand = normalized.substring(0, separatorIndex)
        val encodedPath = if (isWindows()) {
            if (!directoryCommand.startsWith("cd /d ")) return null
            val token = directoryCommand.removePrefix("cd /d ")
            if (token.length < 2 || token.first() != '"' || token.last() != '"') return null
            val path = token.substring(1, token.length - 1)
            if (path.contains('"')) return null
            path
        } else {
            if (!directoryCommand.startsWith("cd ")) return null
            decodeSingleQuotedPath(directoryCommand.removePrefix("cd ")) ?: return null
        }

        val workingDirectory = runCatching { File(encodedPath).canonicalFile }.getOrNull() ?: return null
        val repositoryRoot = runCatching { repository.canonicalFile.toPath().normalize() }.getOrNull() ?: return null
        val workingPath = workingDirectory.toPath().normalize()
        if (!workingDirectory.isDirectory || !workingPath.startsWith(repositoryRoot)) return null

        val invocation = normalized.substring(separatorIndex + 4).trim()
        if (invocation.isEmpty()) return null

        val commandPattern = if (isWindows()) {
            Regex("""^(?:mvnw\.cmd|gradlew\.bat|mvn|gradle)(?: [A-Za-z0-9_./:=+-]+)*$""")
        } else {
            Regex("""^(?:\./mvnw|\./gradlew|mvn|gradle)(?: [A-Za-z0-9_./:=+-]+)*$""")
        }
        if (!commandPattern.matches(invocation)) return null
        return ValidatedRepositoryBuildCommand(workingDirectory, invocation)
    }

    /** Decode the exact single-quote escaping emitted by EngineeringPlanner. */
    private fun decodeSingleQuotedPath(token: String): String? {
        if (token.length < 2 || token.first() != '\'' || token.last() != '\'') return null
        val result = StringBuilder()
        var index = 1
        val contentEnd = token.length - 1
        while (index < contentEnd) {
            if (token.startsWith("'\\''", index)) {
                result.append('\'')
                index += 4
            } else {
                val character = token[index]
                if (character == '\'') return null
                result.append(character)
                index++
            }
        }
        return result.toString()
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
