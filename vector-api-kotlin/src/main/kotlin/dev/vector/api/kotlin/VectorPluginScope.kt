package dev.vector.api.kotlin

import dev.vector.api.ProxyServer
import dev.vector.api.event.EventPriority
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.api.event.VectorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Duration

class VectorPluginScope(
    val server: ProxyServer,
    val logger: Logger,
    @PublishedApi internal val pluginId: String,
    coroutineScope: CoroutineScope,
    internal val classLoader: ClassLoader,
) : CoroutineScope by coroutineScope {

    private val disableHandlers = mutableListOf<suspend VectorPluginScope.() -> Unit>()

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

    fun onDisable(handler: suspend VectorPluginScope.() -> Unit) {
        disableHandlers.add(handler)
    }

    fun migrate(location: String = "db/migration") {
        server.storage.migrate(pluginId, classLoader, location)
    }

    fun command(
        name: String,
        completer: VectorPluginScope.(List<String>) -> List<String> = { emptyList() },
        handler: suspend VectorPluginScope.(List<String>) -> Unit
    ) {
        server.registerCommand(
            name,
            pluginId,
            { args -> this@VectorPluginScope.handler(args) },
            { args -> this@VectorPluginScope.completer(args) }
        )
    }

    fun every(period: Duration, block: suspend VectorPluginScope.() -> Unit): Job =
        launch {
            while (true) {
                delay(period)
                block()
            }
        }

    internal suspend fun runDisable() {
        for (handler in disableHandlers) {
            try { handler() } catch (_: Exception) {}
        }
    }
}
