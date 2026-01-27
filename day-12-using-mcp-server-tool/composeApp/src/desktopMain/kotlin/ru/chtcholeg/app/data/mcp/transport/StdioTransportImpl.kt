package ru.chtcholeg.app.data.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.chtcholeg.app.data.mcp.stub.StdioServerParameters
import ru.chtcholeg.app.data.mcp.stub.Transport
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Desktop implementation of Stdio transport using Process.
 * Launches a subprocess and communicates via stdin/stdout.
 */
class StdioTransportImpl(
    private val params: StdioServerParameters
) : Transport {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun start() = withContext(Dispatchers.IO) {
        try {
            // Build command with arguments
            val command = buildList {
                add(params.command)
                addAll(params.args)
            }

            // Create process builder
            val processBuilder = ProcessBuilder(command).apply {
                // Set environment variables
                if (params.env.isNotEmpty()) {
                    environment().putAll(params.env)
                }
                // Redirect error stream to stdout
                redirectErrorStream(true)
            }

            // Start process
            process = processBuilder.start()

            // Setup streams
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))

            // Start reading output in background
            scope.launch {
                try {
                    reader?.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                responseChannel.send(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error reading from process: ${e.message}")
                }
            }

            println("Started MCP server process: ${params.command} ${params.args.joinToString(" ")}")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to start MCP server process: ${e.message}", e)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
            process?.waitFor()
            responseChannel.close()
            println("Closed MCP server process")
        } catch (e: Exception) {
            println("Error closing process: ${e.message}")
        }
    }

    override suspend fun send(message: String) {
        withContext(Dispatchers.IO) {
            try {
                writer?.let { w ->
                    w.write(message)
                    w.newLine()
                    w.flush()
                } ?: throw IllegalStateException("Transport not started")
            } catch (e: Exception) {
                throw IllegalStateException("Failed to send message: ${e.message}", e)
            }
        }
    }

    override suspend fun receive(): String? {
        return try {
            responseChannel.receive()
        } catch (e: Exception) {
            null
        }
    }
}
