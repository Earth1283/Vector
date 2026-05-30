package dev.vector.ci

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

data class HarnessResult(
    val errors: List<String>,
    val warnings: List<String>,
    val pluginsLoaded: Int,
    val crashed: Boolean,
    val timedOut: Boolean,
)

fun runHarness(proxyJar: Path, pluginsDir: Path, timeoutSecs: Int = 30): HarnessResult {
    val workDir = Files.createTempDirectory("vector-ci-")
    val port = freePort()

    // Minimal config: bind on a free port, no routing (limbo holds all players)
    workDir.resolve("vector.toml").writeText(
        """
        bind = "0.0.0.0:$port"

        [routing]
        try = []

        [limbo]
        unclaimed-action = "hold"
        max-hold-duration = 0
        """.trimIndent()
    )

    val ciPluginsDir = workDir.resolve("plugins")
    Files.createDirectories(ciPluginsDir)
    pluginsDir.toFile().listFiles { f -> f.name.endsWith(".jar") }?.forEach { jar ->
        Files.copy(jar.toPath(), ciPluginsDir.resolve(jar.name), StandardCopyOption.REPLACE_EXISTING)
    }

    val errors   = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    var pluginsLoaded = 0
    var ready   = false
    var crashed = false

    val process = ProcessBuilder("java", "-jar", proxyJar.toAbsolutePath().toString())
        .directory(workDir.toFile())
        .redirectErrorStream(true)
        .start()

    val stdin = process.outputStream.bufferedWriter()

    val readThread = Thread {
        process.inputStream.bufferedReader().useLines { seq ->
            for (line in seq) {
                when {
                    line.contains("Vector is ready") -> ready = true
                    LOADED_RE.containsMatchIn(line)  -> {
                        pluginsLoaded = LOADED_RE.find(line)
                            ?.groupValues?.get(1)?.toIntOrNull() ?: pluginsLoaded
                    }
                    isErrorLine(line) -> errors  += stripAnsi(line)
                    isWarnLine(line)  -> warnings += stripAnsi(line)
                }
            }
        }
    }.also { it.isDaemon = true; it.start() }

    val deadline = System.currentTimeMillis() + timeoutSecs * 1000L
    while (!ready && System.currentTimeMillis() < deadline && process.isAlive) {
        Thread.sleep(200)
    }

    runCatching { stdin.write("stop\n"); stdin.flush() }

    val exited = process.waitFor(15, TimeUnit.SECONDS)
    if (!exited) {
        crashed = true
        process.destroyForcibly()
    } else if (process.exitValue() != 0) {
        crashed = true
    }

    readThread.join(2_000)
    workDir.toFile().deleteRecursively()

    return HarnessResult(
        errors        = errors.distinct(),
        warnings      = warnings.distinct(),
        pluginsLoaded = pluginsLoaded,
        crashed       = crashed,
        timedOut      = !ready,
    )
}

private fun isErrorLine(line: String): Boolean =
    line.contains(" ERROR ") ||
    (line.contains("Exception") && (line.contains(": ") || line.trimStart().startsWith("at ")))

private fun isWarnLine(line: String): Boolean =
    line.contains(" WARN ") &&
    (line.contains("plugin", ignoreCase = true) || line.contains("load", ignoreCase = true) ||
     line.contains("enable", ignoreCase = true) || line.contains("fail", ignoreCase = true))

private val LOADED_RE = Regex("""Loaded (\d+) plugin""")
// \x1B matches the ESC prefix of ANSI CSI sequences: ESC [ ... X
private val ANSI_RE   = Regex("""\x1B\[[0-9;]*[A-Za-z]""")
private fun stripAnsi(s: String) = ANSI_RE.replace(s, "").trim()

private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }
