package info.benjaminhill.llamaflags

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CommandExecutor {

    /**
     * Executes a given command, capturing and printing its output in real-time.
     *
     * @param commandParts The command to execute (e.g., "ping google.com").
     * @param timeout The maximum time to wait for the command to complete. Defaults to 5 minutes.
     * @param reportInterval The interval at which to report output metrics. Defaults to 10 seconds.
     */
    suspend fun execute(
        commandParts: List<String>,
        timeout: kotlin.time.Duration = 5.minutes,
        reportInterval: kotlin.time.Duration = 10.seconds
    ): Triple<ExecutionResult, Double, Double> {
        val processBuilder = ProcessBuilder(commandParts)
            .redirectErrorStream(true) // Merges stderr into stdout for easier reading.

        println("[INFO] Executing command: \"${commandParts.joinToString(" ")}\" with a ${timeout.inWholeMinutes}-minute timeout.")
        println("---")

        var wps = 0.0
        var tps = 0.0
        try {
            // withTimeout will throw a TimeoutCancellationException if the block does not complete
            // within the specified time. This is the core of our timeout mechanism.
            withTimeout(timeout) {
                // coroutineScope ensures that all launched child coroutines (for reading and reporting)
                // are cancelled and cleaned up when this scope exits, either normally or through an exception.
                coroutineScope {
                    var process: Process? = null
                    val totalWords = AtomicLong(0)
                    val startTime = System.currentTimeMillis()


                    try {
                        // Start the external process.
                        process = processBuilder.start()
                        val processIsAlive = { process?.isAlive == true }

                        // Coroutine #1: Periodically reports the words-per-second metric.
                        launch(context = Dispatchers.Default) {
                            while (isActive && processIsAlive()) {
                                delay(duration = reportInterval)
                                // Re-check after delay to ensure the process hasn't terminated in the meantime.
                                if (processIsAlive()) {
                                    val elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                                    val words = totalWords.get()
                                    wps = if (elapsedTimeSeconds > 0) words / elapsedTimeSeconds else 0.0
                                    println("[METRIC] Words/sec: ${"%.2f".format(wps)} | Total words: $words | Elapsed: ${elapsedTimeSeconds.toInt()}s")
                                }
                            }
                        }

                        // Coroutine #2: Reads the process output stream.
                        val readerJob = launch(Dispatchers.IO) {
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            reader.use { r ->
                                var line: String?
                                // Read line by line until the stream is closed (process terminates).
                                while (r.readLine().also { line = it } != null) {
                                    println(line) // Print output in real-time.
                                    val wordsInLine = line?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
                                    totalWords.addAndGet(wordsInLine.toLong())
                                    Regex("llama_perf_context_print:.+(\\S+) tokens per second").find(line ?: "")?.groupValues?.first()?.let {
                                        tps = it.toDouble()
                                    }
                                }
                            }
                        }

                        // Wait for the process to complete and get its exit code.
                        readerJob.join()
                        process.waitFor()
                    } finally {
                        if (process?.isAlive == true) {
                            println("[INFO] Cleaning up and destroying the process...")
                            process.destroyForcibly()
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            return Triple(ExecutionResult.TIMEOUT, wps, tps)
        } catch (e: Exception) {
            println("\n---\n[ERROR] An unexpected error occurred: ${e.message}")
            e.printStackTrace()
            return Triple(ExecutionResult.FAILURE, wps, tps)
        }
        return Triple(ExecutionResult.SUCCESS, wps, tps)
    }

    enum class ExecutionResult {
        SUCCESS,
        TIMEOUT,
        FAILURE
    }

    companion object {
        /**
         * Builds a command list from the base command and a map of parameters.
         */
        fun buildCommand(baseCommand: List<String>, params: Map<String, Any>): List<String> {
            val cmd = baseCommand.toMutableList()
            for ((key, value) in params) {
                when (value) {
                    is Boolean -> {
                        if (value) cmd.add(key)
                    }

                    else -> {
                        cmd.add(key)
                        cmd.add(value.toString())
                    }
                }
            }
            return cmd
        }
    }
}
