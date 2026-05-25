package dev.vector.api.event

import kotlin.reflect.KClass

interface EventBus {
    fun <T : VectorEvent> register(
        eventClass: KClass<T>,
        pluginId: String,
        priority: EventPriority,
        handler: suspend (T) -> Unit,
    )

    fun unregisterAll(pluginId: String)

    suspend fun <T : VectorEvent> fire(event: T): T
}
