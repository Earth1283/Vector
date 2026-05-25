package dev.vector.proxy.event

import dev.vector.api.event.CancellableEvent
import dev.vector.api.event.EventBus
import dev.vector.api.event.EventPriority
import dev.vector.api.event.VectorEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

class EventBusImpl : EventBus {

    private data class HandlerEntry(
        val pluginId: String,
        val priority: EventPriority,
        val handler: suspend (Any) -> Unit,
    )

    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<HandlerEntry>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : VectorEvent> register(
        eventClass: KClass<T>,
        pluginId: String,
        priority: EventPriority,
        handler: suspend (T) -> Unit,
    ) {
        handlers.getOrPut(eventClass) { CopyOnWriteArrayList() }
            .add(HandlerEntry(pluginId, priority, handler as suspend (Any) -> Unit))
    }

    override fun unregisterAll(pluginId: String) {
        handlers.values.forEach { list -> list.removeIf { it.pluginId == pluginId } }
    }

    override suspend fun <T : VectorEvent> fire(event: T): T {
        val list = handlers[event::class] ?: return event
        for (entry in list.sortedBy { it.priority }) {
            if (event is CancellableEvent && event.isCancelled && entry.priority != EventPriority.MONITOR) continue
            entry.handler(event)
        }
        return event
    }
}
