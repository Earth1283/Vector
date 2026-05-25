package dev.vector.api.kotlin

import dev.vector.api.ProxyServer
import dev.vector.api.event.EventPriority
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.api.event.VectorEvent
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger

class VectorPluginScope(
    val server: ProxyServer,
    val logger: Logger,
    @PublishedApi internal val pluginId: String,
    coroutineScope: CoroutineScope,
) : CoroutineScope by coroutineScope {

    inline fun <reified T : VectorEvent> on(
        priority: EventPriority = EventPriority.NORMAL,
        noinline handler: suspend VectorPluginScope.(T) -> Unit,
    ) {
        server.eventBus.register(T::class, pluginId, priority) { event ->
            this@VectorPluginScope.handler(event)
        }
    }

    fun onEnable(handler: suspend VectorPluginScope.(ProxyInitializeEvent) -> Unit) =
        on(EventPriority.NORMAL, handler)
}
