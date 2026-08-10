package com.opendroid.ai.core.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalSessionInfo(
    val id: String,
    val backend: CommandBackend
)

@Singleton
class PersistentTerminalManager @Inject constructor(
    private val commandExecutor: PrivilegedCommandExecutor
) {

    private data class TerminalSession(
        val process: Process,
        val backend: CommandBackend,
        val writer: BufferedWriter,
        val output: StringBuilder = StringBuilder(),
        val lock: Any = Any()
    )

    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    suspend fun create(): TerminalSessionInfo = withContext(Dispatchers.IO) {
        val (backend, process) = commandExecutor.startShell()
        val id = UUID.randomUUID().toString()
        val session = TerminalSession(
            process = process,
            backend = backend,
            writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        )
        sessions[id] = session
        startReader(session, process.inputStream)
        startReader(session, process.errorStream)
        TerminalSessionInfo(id, backend)
    }

    suspend fun write(id: String, command: String) = withContext(Dispatchers.IO) {
        require(command.length <= MAX_COMMAND_LENGTH) { "Command is too long" }
        val session = requireSession(id)
        synchronized(session.lock) {
            check(session.process.isAlive) { "Terminal session has exited" }
            session.writer.write(command)
            session.writer.newLine()
            session.writer.flush()
        }
    }

    suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        val session = requireSession(id)
        synchronized(session.lock) {
            val result = session.output.toString()
            session.output.setLength(0)
            result
        }
    }

    fun list(): List<TerminalSessionInfo> = sessions.entries.map { (id, session) ->
        TerminalSessionInfo(id, session.backend)
    }

    fun close(id: String) {
        sessions.remove(id)?.let { session ->
            synchronized(session.lock) {
                runCatching { session.writer.close() }
                session.process.destroy()
            }
        }
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
    }

    private fun requireSession(id: String): TerminalSession = sessions[id]
        ?: throw IllegalArgumentException("Unknown terminal session: $id")

    private fun startReader(session: TerminalSession, stream: java.io.InputStream) {
        Thread {
            val buffer = ByteArray(8192)
            stream.use { input ->
                while (true) {
                    val count = try {
                        input.read(buffer)
                    } catch (_: Exception) {
                        break
                    }
                    if (count < 0) break
                    if (count == 0) continue
                    synchronized(session.lock) {
                        appendLimited(session.output, String(buffer, 0, count, Charsets.UTF_8))
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "OpenDroid-TerminalReader"
            start()
        }
    }

    private fun appendLimited(output: StringBuilder, value: String) {
        if (value.length >= MAX_OUTPUT_CHARS) {
            output.setLength(0)
            output.append(value.takeLast(MAX_OUTPUT_CHARS))
            return
        }
        val overflow = output.length + value.length - MAX_OUTPUT_CHARS
        if (overflow > 0) output.delete(0, overflow)
        output.append(value)
    }

    private companion object {
        const val MAX_COMMAND_LENGTH = 4096
        const val MAX_OUTPUT_CHARS = 64 * 1024
    }
}
