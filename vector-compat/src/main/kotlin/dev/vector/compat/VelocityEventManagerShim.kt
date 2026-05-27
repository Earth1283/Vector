package dev.vector.compat

import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.vector.api.ProxyServer
import dev.vector.api.event.EventPriority
import dev.vector.api.event.PlayerJoinEvent
import dev.vector.api.event.PlayerLeaveEvent
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class VelocityEventManagerShim(private val vectorServer: ProxyServer) : EventManager {

    private val logger = LoggerFactory.getLogger(VelocityEventManagerShim::class.java)

    private data class SubscriberEntry(
        val plugin: Any,
        val listener: Any,
        val method: Method,
        val order: PostOrder,
    )

    private data class FunctionalEntry<E>(
        val plugin: Any,
        val handler: EventHandler<E>,
        val priority: Short,
    )

    private val subscribers = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<SubscriberEntry>>()

    @Suppress("UNCHECKED_CAST")
    private val functional = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<FunctionalEntry<*>>>()

    init {
        vectorServer.eventBus.register(PlayerJoinEvent::class, "vector-compat", EventPriority.NORMAL) { event ->
            fireAndForget(PostLoginEvent(VelocityPlayerShim(event.player, vectorServer)))
        }
        vectorServer.eventBus.register(PlayerLeaveEvent::class, "vector-compat", EventPriority.NORMAL) { event ->
            fireAndForget(DisconnectEvent(VelocityPlayerShim(event.player, vectorServer), DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN))
        }
        vectorServer.eventBus.register(dev.vector.api.event.ProxyInitializeEvent::class, "vector-compat", EventPriority.NORMAL) { _ ->
            fireAndForget(com.velocitypowered.api.event.proxy.ProxyInitializeEvent())
        }
        vectorServer.eventBus.register(dev.vector.api.event.ProxyShutdownEvent::class, "vector-compat", EventPriority.NORMAL) { _ ->
            fireAndForget(com.velocitypowered.api.event.proxy.ProxyShutdownEvent())
        }
    }

    override fun fireAndForget(event: Any) {
        fire(event)
    }

    override fun register(plugin: Any, listener: Any) {
        listener.javaClass.methods
            .filter { it.isAnnotationPresent(Subscribe::class.java) && it.parameterCount == 1 }
            .forEach { method ->
                val eventType = method.parameterTypes[0]
                val order = method.getAnnotation(Subscribe::class.java).order
                subscribers.getOrPut(eventType) { CopyOnWriteArrayList() }
                    .add(SubscriberEntry(plugin, listener, method, order))
                logger.debug("Registered @Subscribe handler {}.{} for {}",
                    listener.javaClass.simpleName, method.name, eventType.simpleName)
            }
    }

    override fun <E : Any> register(plugin: Any, clazz: Class<E>, order: PostOrder, handler: EventHandler<E>) {
        register(plugin, clazz, order.ordinal.toShort(), handler)
    }

    override fun <E : Any> register(plugin: Any, clazz: Class<E>, priority: Short, handler: EventHandler<E>) {
        @Suppress("UNCHECKED_CAST")
        (functional.getOrPut(clazz) { CopyOnWriteArrayList() } as CopyOnWriteArrayList<FunctionalEntry<E>>)
            .add(FunctionalEntry(plugin, handler, priority))
    }

    override fun <E : Any> fire(event: E): CompletableFuture<E> {
        val eventClass = event::class.java

        val subs = subscribers[eventClass]
        if (subs != null) {
            subs.sortedBy { it.order.ordinal }.forEach { entry ->
                try {
                    entry.method.isAccessible = true
                    entry.method.invoke(entry.listener, event)
                } catch (t: Throwable) {
                    val cause = if (t is java.lang.reflect.InvocationTargetException) t.targetException else t
                    logger.error("Error in @Subscribe handler {}.{} for {}: {}",
                        entry.listener.javaClass.simpleName, entry.method.name, eventClass.simpleName, cause.message, cause)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val fns = functional[eventClass] as? CopyOnWriteArrayList<FunctionalEntry<E>>
        fns?.sortedByDescending { it.priority }?.forEach { fn ->
            try {
                fn.handler.execute(event)
            } catch (e: Exception) {
                logger.error("Exception in functional handler for {}", eventClass.simpleName, e)
            }
        }

        return CompletableFuture.completedFuture(event)
    }

    override fun unregisterListeners(plugin: Any) {
        subscribers.values.forEach { it.removeIf { e -> e.plugin === plugin } }
        functional.values.forEach { it.removeIf { e -> e.plugin === plugin } }
    }

    override fun unregisterListener(plugin: Any, listener: Any) {
        subscribers.values.forEach { it.removeIf { e -> e.plugin === plugin && e.listener === listener } }
    }

    override fun <E : Any> unregister(plugin: Any, handler: EventHandler<E>) {
        functional.values.forEach { it.removeIf { e -> e.plugin === plugin && e.handler === handler } }
    }
}
