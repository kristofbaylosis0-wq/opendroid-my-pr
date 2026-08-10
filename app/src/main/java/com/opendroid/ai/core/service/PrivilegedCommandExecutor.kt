package com.opendroid.ai.core.service

import android.content.pm.PackageManager
import android.util.Log
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CommandBackend {
    SHIZUKU,
    ROOT,
    APP_SHELL,
    UNAVAILABLE
}

data class CommandExecutionResult(
    val backend: CommandBackend,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

@Singleton
class PrivilegedCommandExecutor @Inject constructor() {

    suspend fun execute(command: String): CommandExecutionResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Command must not be empty" }
        require(command.length <= MAX_COMMAND_LENGTH) { "Command is too long" }

        var lastFailure: Throwable? = null
        for (backend in availableBackends()) {
            try {
                return@withContext when (backend) {
                    CommandBackend.SHIZUKU -> runShizuku(command, backend)
                    CommandBackend.ROOT -> runProcess(arrayOf("su", "-c", command), backend)
                    CommandBackend.APP_SHELL -> runProcess(arrayOf("sh", "-c", command), backend)
                    CommandBackend.UNAVAILABLE -> error("Unreachable backend")
                }
            } catch (error: Throwable) {
                lastFailure = error
                Log.d(TAG, "Command backend $backend failed", error)
            }
        }

        CommandExecutionResult(
            backend = CommandBackend.UNAVAILABLE,
            exitCode = -1,
            stdout = "",
            stderr = lastFailure?.message ?: "No command execution backend is available"
        )
    }

    fun status(): Map<String, String> = mapOf(
        "backend" to (availableBackends().firstOrNull() ?: CommandBackend.UNAVAILABLE).name,
        "shizuku" to shizukuStatus(),
        "root" to if (rootAvailable()) "available" else "unavailable"
    )

    fun startShell(): Pair<CommandBackend, Process> {
        var lastFailure: Throwable? = null
        for (backend in availableBackends()) {
            try {
                return when (backend) {
                    CommandBackend.SHIZUKU -> backend to Shizuku.newProcess(arrayOf("sh"), null, null)
                    CommandBackend.ROOT -> backend to ProcessBuilder("su").redirectErrorStream(true).start()
                    CommandBackend.APP_SHELL -> backend to ProcessBuilder("sh").redirectErrorStream(true).start()
                    CommandBackend.UNAVAILABLE -> error("Unreachable backend")
                }
            } catch (error: Throwable) {
                lastFailure = error
                Log.d(TAG, "Shell backend $backend failed", error)
            }
        }
        throw IllegalStateException(lastFailure?.message ?: "No command execution backend is available", lastFailure)
    }

    private fun availableBackends(): List<CommandBackend> = buildList {
        if (shizukuAvailable()) add(CommandBackend.SHIZUKU)
        if (rootAvailable()) add(CommandBackend.ROOT)
        add(CommandBackend.APP_SHELL)
    }

    private fun shizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (error: Throwable) {
        Log.d(TAG, "Shizuku unavailable", error)
        false
    }

    private fun shizukuStatus(): String = try {
        when {
            !Shizuku.pingBinder() -> "not_running"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "authorized"
            else -> "permission_required"
        }
    } catch (_: Throwable) {
        "unavailable"
    }

    private fun rootAvailable(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id").start()
        try {
            process.inputStream.close()
            process.errorStream.close()
            process.waitFor() == 0
        } finally {
            process.destroy()
        }
    } catch (_: Throwable) {
        false
    }

    private fun runShizuku(command: String, backend: CommandBackend): CommandExecutionResult {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        return readProcess(process, backend)
    }

    private fun runProcess(command: Array<String>, backend: CommandBackend): CommandExecutionResult =
        readProcess(ProcessBuilder(*command).start(), backend)

    private fun readProcess(process: Process, backend: CommandBackend): CommandExecutionResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = Thread { readLimited(process.inputStream, stdout) }
        val stderrThread = Thread { readLimited(process.errorStream, stderr) }
        stdoutThread.start()
        stderrThread.start()
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        stdoutThread.join(OUTPUT_JOIN_TIMEOUT_MS)
        stderrThread.join(OUTPUT_JOIN_TIMEOUT_MS)
        return CommandExecutionResult(
            backend,
            if (completed) process.exitValue() else TIMEOUT_EXIT_CODE,
            stdout.toString(),
            if (completed) stderr.toString() else "${stderr}Command timed out"
        )
    }

    private fun readLimited(stream: java.io.InputStream, output: StringBuilder) {
        val buffer = ByteArray(8192)
        var remaining = MAX_OUTPUT_BYTES
        stream.use {
            while (remaining > 0) {
                val count = it.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.append(String(buffer, 0, count, Charsets.UTF_8))
                remaining -= count
            }
        }
    }

    private companion object {
        const val TAG = "PrivilegedCommandExecutor"
        const val MAX_COMMAND_LENGTH = 4096
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val COMMAND_TIMEOUT_SECONDS = 30L
        const val OUTPUT_JOIN_TIMEOUT_MS = 1000L
        const val TIMEOUT_EXIT_CODE = 124
    }
}
