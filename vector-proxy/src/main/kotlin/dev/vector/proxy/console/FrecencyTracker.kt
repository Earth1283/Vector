package dev.vector.proxy.console

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln

/**
 * Tracks how frequently and recently items are used, scoring them with
 * half-life decay so that recent usage outweighs old frequency.
 *
 * Memory: ~historySize * 8 bytes per tracked item.
 * At historySize=256 and 3 000 distinct commands ≈ 6 MB.
 */
class FrecencyTracker(private val historySize: Int = 256) {

    private inner class Entry {
        val history = RingBuffer<Long>(historySize)
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun record(item: String) {
        entries.getOrPut(item) { Entry() }.history.push(System.currentTimeMillis())
    }

    fun score(item: String): Double {
        val entry = entries[item] ?: return 0.0
        val now = System.currentTimeMillis()
        return entry.history.snapshot().sumOf { ts ->
            exp(-LAMBDA * (now - ts))
        }
    }

    fun ranked(candidates: List<String>): List<String> =
        candidates.sortedByDescending { score(it) }

    fun knownItems(): List<String> =
        entries.keys.sortedByDescending { score(it) }

    companion object {
        private const val HALF_LIFE_MS = 3_600_000.0
        private val LAMBDA = ln(2.0) / HALF_LIFE_MS
    }
}
