package dev.vector.proxy.console

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

class FrecencyCompleter(
    val tracker: FrecencyTracker,
    private val staticCommands: () -> List<String>,
    private val argProvider: (command: String, argIndex: Int) -> List<String>,
) : Completer {

    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        val wordIndex = line.wordIndex()
        val word = line.word()

        if (wordIndex == 0) {
            // Completing the command name itself
            val all = (staticCommands() + tracker.knownItems()).distinct()
            val filtered = if (word.isEmpty()) all else all.filter { it.startsWith(word, ignoreCase = true) }
            tracker.ranked(filtered).forEach { cmd -> candidates.add(Candidate(cmd)) }
        } else {
            // Completing an argument — delegate to the provider, no frecency ranking
            val command = line.words().getOrElse(0) { "" }
            val argIndex = wordIndex - 1
            val args = argProvider(command, argIndex)
            val filtered = if (word.isEmpty()) args else args.filter { it.startsWith(word, ignoreCase = true) }
            filtered.forEach { arg -> candidates.add(Candidate(arg)) }
        }
    }
}
