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
        synchronized(handlers) {
            val list = handlers.getOrPut(eventClass) { CopyOnWriteArrayList() }
            list.add(HandlerEntry(pluginId, priority, handler as suspend (Any) -> Unit))
            val sorted = list.sortedBy { it.priority }
            handlers[eventClass] = CopyOnWriteArrayList(sorted)
        }
    }

    override fun unregisterAll(pluginId: String) {
        synchronized(handlers) {
            handlers.forEach { (eventClass, list) ->
                val updated = list.filter { it.pluginId != pluginId }
                handlers[eventClass] = CopyOnWriteArrayList(updated)
            }
        }
    }

    override suspend fun <T : VectorEvent> fire(event: T): T {
        val list = handlers[event::class] ?: return event
        val logger = org.slf4j.LoggerFactory.getLogger(EventBusImpl::class.java)
        for (entry in list) {
            if (event is CancellableEvent && event.isCancelled && entry.priority != EventPriority.MONITOR) continue
            try {
                entry.handler(event)
            } catch (e: Throwable) {
                logger.error("Error firing event {} for plugin {}: {}", event::class.simpleName, entry.pluginId, e.message ?: e.toString(), e)
            }
        }
        return event
    }
}
