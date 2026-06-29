package dev.vector.api.event

import java.util.function.Consumer
import kotlin.reflect.KClass

fun <T : VectorEvent> EventBus.register(
    eventClass: Class<T>,
    pluginId: String,
    priority: EventPriority,
    handler: Consumer<T>,
) {
    @Suppress("UNCHECKED_CAST")
    register(eventClass.kotlin as KClass<T>, pluginId, priority) { event ->
        handler.accept(event)
    }
}

fun <T : VectorEvent> EventBus.register(
    eventClass: Class<T>,
    pluginId: String,
    handler: Consumer<T>,
) = register(eventClass, pluginId, EventPriority.NORMAL, handler)
