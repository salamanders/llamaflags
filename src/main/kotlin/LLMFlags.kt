package info.benjaminhill.llamaflags

import info.benjaminhill.llamaflags.CommandExecutor.Companion.buildCommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.measureTimedValue


data class Stat(
    val params: Map<String, Any>,
    val result: CommandExecutor.ExecutionResult,
    val time: Duration,
    val wps: Double,
    val tps: Double,
)

// Define the command and its base parameters
val BASE_COMMAND = listOf(
    "/home/benjamin/llama.cpp/build/bin/llama-cli",
    "-m",
    "/home/benjamin/projects/models/devstralQ4_0.gguf",
    "--color",
    "-p",
    "\"The top 10 words when you think of LLM coding.  Just the words, one per line, in order of importance.\"",
    "--temp",
    "0",
    "-n",
    "128",
    "-no-cnv",
)

// Define the parameter sets to test.
// For boolean flags like "--flash-attn", true adds the flag, false omits it.
val PARAMS_TO_TEST = mapOf(
    "-ngl" to listOf(24, 28, 32),
    "--ubatch-size" to listOf(256, 512, 1024, 2048),
    "--batch-size" to listOf(256, 512, 1024, 2048, 4096),
    "--threads" to listOf(2, 4),
    "--flash-attn" to listOf(true, false)
)

fun printStats(allStats: List<Stat>) {
    println("There were ${allStats.count { it.result == CommandExecutor.ExecutionResult.SUCCESS }} successful runs out of ${allStats.size} attempts")
    println("Max WPS: ${allStats.filter { it.result == CommandExecutor.ExecutionResult.SUCCESS }.maxBy { it.wps }}")
    println("Max TPS: ${allStats.filter { it.result == CommandExecutor.ExecutionResult.SUCCESS }.maxBy { it.tps }}")
}

fun main() = runBlocking {
    val commandExecutor = CommandExecutor()
    val combinations = product(PARAMS_TO_TEST).shuffled()
    val logFile = File("llamaflags_log.csv")

    // Write CSV header
    logFile.writeText("parameters,result,time_seconds,wps,tps\n")

    println("🧪 Starting benchmark of ${combinations.size} parameter combinations...")
    val allStats = mutableListOf<Stat>()
    combinations.forEach { params ->
        val commandParts = buildCommand(BASE_COMMAND, params)
        val result = measureTimedValue {
            commandExecutor.execute(commandParts)
        }
        val duration = result.duration
        val (outcome, wps, tps) = result.value
        Stat(
            params = params,
            result = outcome,
            time = duration,
            wps = wps,
            tps = tps,
        ).also { newStat ->
            allStats.add(newStat)
            // Log to CSV file
            val paramsJson = Json.encodeToString(newStat.params.mapValues { it.value.toString() })
            val logEntry = "\"${
                paramsJson.replace(
                    "\"",
                    "\"\""
                )
            }\",${newStat.result},${newStat.time.inWholeSeconds},${newStat.wps},${newStat.tps}\n"
            logFile.appendText(logEntry)

            if (newStat.result == CommandExecutor.ExecutionResult.SUCCESS) {
                println(newStat)
                printStats(allStats)
            }
        }
    }

}