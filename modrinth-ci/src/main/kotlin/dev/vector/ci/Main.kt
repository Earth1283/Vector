package dev.vector.ci

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var limit       = 20
    var proxyJar: Path? = null
    var timeoutSecs = 45
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--limit"     -> limit       = args[++i].toInt()
            "--proxy-jar" -> proxyJar    = Paths.get(args[++i])
            "--timeout"   -> timeoutSecs = args[++i].toInt()
        }
        i++
    }

    // - Resolve proxy JAR
    val jar = proxyJar ?: findProxyJar()
    if (jar == null || !jar.toFile().exists()) {
        System.err.println("[VecCI] ERROR: proxy JAR not found.")
        System.err.println("[VecCI] Build with: ./gradlew :vector-proxy:shadowJar")
        System.err.println("[VecCI] Or pass:    --proxy-jar <path>")
        exitProcess(1)
    }
    println("[VecCI] Proxy JAR : $jar")
    println("[VecCI] Limit     : $limit")
    println("[VecCI] Timeout   : ${timeoutSecs}s")
    println()

    // - Step 1: Query Modrinth for top Velocity plugins
    println("[VecCI] Querying Modrinth for top $limit Velocity plugins...")
    val plugins = try {
        searchVelocityPlugins(limit)
    } catch (e: Exception) {
        System.err.println("[VecCI] ERROR: Modrinth query failed: ${e.message}")
        exitProcess(1)
    }
    println("[VecCI] ${plugins.size} plugin(s) found:")
    plugins.forEachIndexed { n, p ->
        println("  %2d. %-40s %6d followers".format(n + 1, "${p.title} (${p.slug})", p.follows))
    }
    println()

    // - Step 2: Download JARs
    val downloadDir = Paths.get("build/ci-plugins").also { Files.createDirectories(it) }
    val downloaded  = mutableListOf<PluginInfo>()
    val noRelease   = mutableListOf<PluginInfo>()

    for (plugin in plugins) {
        val cached = downloadDir.resolve("${plugin.slug}.jar")
        if (cached.toFile().exists()) {
            print("[VecCI] ✓ ${plugin.slug} (cached)")
            println()
            downloaded += plugin
            continue
        }
        print("[VecCI] ↓ ${plugin.slug}...")
        try {
            val vf = resolveVelocityJar(plugin.id)
            if (vf == null) {
                println(" no Velocity release — skip")
                noRelease += plugin
                continue
            }
            print(" ${vf.filename}...")
            downloadJar(vf.url, downloadDir, "${plugin.slug}.jar")
            println(" done")
            downloaded += plugin
        } catch (e: Exception) {
            println(" FAILED: ${e.message}")
            noRelease += plugin
        }
    }

    println()
    println("[VecCI] ${downloaded.size} plugin(s) to test, ${noRelease.size} had no Velocity release")

    if (downloaded.isEmpty()) {
        println("[VecCI] Nothing to test.")
        return
    }

    // - Step 3: Run harness
    println("[VecCI] Starting Vector harness (timeout=${timeoutSecs}s)...")
    val result = runHarness(jar, downloadDir, timeoutSecs)

    // - Step 4: Report results
    println()
    println("[VecCI] ══════════════════ RESULTS ══════════════════")
    println("[VecCI] Plugins supplied : ${downloaded.size}")
    println("[VecCI] Plugins loaded   : ${result.pluginsLoaded}")
    println("[VecCI] Errors           : ${result.errors.size}")
    println("[VecCI] Warnings         : ${result.warnings.size}")
    println("[VecCI] Proxy crashed    : ${result.crashed}")
    println("[VecCI] Timed out        : ${result.timedOut}")
    println("[VecCI] ════════════════════════════════════════════")

    if (result.warnings.isNotEmpty()) {
        println("[VecCI] Warnings:")
        result.warnings.forEach { println("  $it") }
    }
    if (result.errors.isNotEmpty()) {
        println("[VecCI] Errors:")
        result.errors.forEach { println("  $it") }
    }

    val failed = result.errors.isNotEmpty() || result.crashed
    if (!failed) {
        println("[VecCI] All clean ✓")
        return
    }

    // - Step 5: File GitHub issue
    val today = LocalDate.now()
    val title = "[VecCI] Plugin compat failure — $today"
    val body  = buildIssueBody(today, downloaded, noRelease, result, timeoutSecs)

    println()
    println("[VecCI] Filing GitHub issue: $title")
    createGitHubIssue(title, body)

    exitProcess(1)
}

private fun buildIssueBody(
    today: LocalDate,
    tested: List<PluginInfo>,
    skipped: List<PluginInfo>,
    result: HarnessResult,
    timeoutSecs: Int,
): String = buildString {
    appendLine("## Vector Modrinth CI — $today")
    appendLine()
    appendLine("**${tested.size}** top Velocity plugin(s) tested against Vector proxy.")
    appendLine()

    if (result.crashed)  appendLine("⚠️ **Proxy crashed** — process exited with non-zero code")
    if (result.timedOut) appendLine("⚠️ **Harness timed out** — proxy did not reach ready state within ${timeoutSecs}s")

    appendLine()
    appendLine("| | |")
    appendLine("|---|---|")
    appendLine("| Plugins loaded | ${result.pluginsLoaded} / ${tested.size} |")
    appendLine("| Errors | ${result.errors.size} |")
    appendLine("| Warnings | ${result.warnings.size} |")
    appendLine()

    if (result.errors.isNotEmpty()) {
        appendLine("### Error output")
        appendLine("```")
        result.errors.take(60).forEach { appendLine(it) }
        if (result.errors.size > 60) appendLine("... (${result.errors.size - 60} more lines truncated)")
        appendLine("```")
        appendLine()
    }

    if (result.warnings.isNotEmpty()) {
        appendLine("### Warning output")
        appendLine("```")
        result.warnings.take(20).forEach { appendLine(it) }
        if (result.warnings.size > 20) appendLine("... (${result.warnings.size - 20} more)")
        appendLine("```")
        appendLine()
    }

    appendLine("### Plugins tested")
    tested.forEach { p ->
        appendLine("- [${p.title}](https://modrinth.com/plugin/${p.slug})  (${p.follows} followers)")
    }

    if (skipped.isNotEmpty()) {
        appendLine()
        appendLine("### Skipped (no Velocity release on Modrinth)")
        skipped.forEach { p -> appendLine("- ${p.title} (${p.slug})") }
    }

    appendLine()
    appendLine("*Auto-filed by modrinth-ci harness*")
}

private fun findProxyJar(): Path? {
    for (base in listOf("vector-proxy/build/libs", "../vector-proxy/build/libs")) {
        val dir = Paths.get(base).toFile()
        if (!dir.exists()) continue
        return dir.listFiles { f -> f.name.endsWith(".jar") && !f.name.contains("sources") }
            ?.maxByOrNull { it.lastModified() }?.toPath()
    }
    return null
}
