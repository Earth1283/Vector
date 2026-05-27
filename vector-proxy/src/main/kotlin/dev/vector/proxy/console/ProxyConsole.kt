package dev.vector.proxy.console

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.widget.AutosuggestionWidgets
import java.io.Closeable

class ProxyConsole(val theme: ConsoleTheme) : Closeable {

    val terminal: Terminal = TerminalBuilder.builder()
        .system(true)
        .name("Vector")
        .build()

    val frecency = FrecencyTracker()

    // Set after construction to provide context-sensitive argument completions.
    var argumentProvider: (command: String, argIndex: Int) -> List<String> = { _, _ -> emptyList() }

    private val completer = FrecencyCompleter(
        tracker = frecency,
        staticCommands = { builtinCommands },
        argProvider = { cmd, idx -> argumentProvider(cmd, idx) },
    )

    val reader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .option(LineReader.Option.AUTO_FRESH_LINE, true)
        .variable(LineReader.BELL_STYLE, "none")
        .build()

    private val autoSuggest = AutosuggestionWidgets(reader).also { it.enable() }

    var builtinCommands: List<String> = listOf(
        "stop", "reload", "plugins", "players",
        "help", "version", "uptime", "servers", "serverctl", "kick", "broadcast",
    )

    fun printAbove(message: String) {
        if (reader.isReading) {
            reader.printAbove(message)
        } else {
            terminal.writer().println(message)
            terminal.writer().flush()
        }
    }

    fun startReadLoop(scope: CoroutineScope, onCommand: suspend (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val line = try {
                    reader.readLine(PROMPT)
                } catch (_: UserInterruptException) {
                    onCommand("stop")
                    break
                } catch (_: EndOfFileException) {
                    onCommand("stop")
                    break
                }
                if (line.isNotBlank()) {
                    frecency.record(line.trim().substringBefore(' '))
                    onCommand(line.trim())
                }
            }
        }
    }

    fun shutdown() {
        try { autoSuggest.disable() } catch (_: Exception) {}
        try {
            // Move past the prompt line so it doesn't linger on screen
            terminal.writer().print("\r[K")
            terminal.writer().flush()
        } catch (_: Exception) {}
        try { terminal.close() } catch (_: Exception) {}
    }

    override fun close() = shutdown()

    companion object {
        private const val PROMPT = "> "
    }
}
