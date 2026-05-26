package dev.vector.proxy.console

class RingBuffer<T>(val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity)
    private var head = 0
    private var count = 0

    @Synchronized
    fun push(item: T) {
        buffer[head] = item
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun snapshot(): List<T> {
        if (count == 0) return emptyList()
        val result = ArrayList<T>(count)
        val start = if (count < capacity) 0 else head
        @Suppress("UNCHECKED_CAST")
        repeat(count) { i -> result.add(buffer[(start + i) % capacity] as T) }
        return result
    }
}
