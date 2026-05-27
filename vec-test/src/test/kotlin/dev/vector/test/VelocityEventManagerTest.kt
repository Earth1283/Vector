package dev.vector.test

import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.vector.api.ProxyServer
import dev.vector.api.VectorPlayer
import dev.vector.api.event.EventBus
import dev.vector.api.event.EventPriority
import dev.vector.api.event.VectorEvent
import dev.vector.api.storage.StorageBackend
import dev.vector.compat.VelocityEventManagerShim
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.reflect.KClass

class VelocityEventManagerTest {

    private val fakeServer = object : ProxyServer {
        override val version = "test"
        override val eventBus = object : EventBus {
            override fun <T : VectorEvent> register(
                eventClass: KClass<T>, pluginId: String, priority: EventPriority, handler: suspend (T) -> Unit,
            ) {}
            override fun unregisterAll(pluginId: String) {}
            override suspend fun <T : VectorEvent> fire(event: T): T = event
        }
        override val storage: StorageBackend get() = throw UnsupportedOperationException()
        override val players: Collection<VectorPlayer> = emptyList()
        override val servers: Collection<dev.vector.api.BackendServer> = emptyList()
        override fun getPlayer(uuid: UUID): VectorPlayer? = null
        override fun getPlayer(username: String): VectorPlayer? = null
        override fun registerCommand(name: String, pluginId: String, handler: suspend (List<String>) -> Unit) {}
        override fun unregisterCommands(pluginId: String) {}
    }

    @Test
    fun `register and fire @Subscribe method`() {
        val mgr = VelocityEventManagerShim(fakeServer)
        var received: String? = null

        val listener = object {
            @Subscribe
            fun onCustom(event: CustomEvent) {
                received = event.payload
            }
        }

        mgr.register(listener, listener)
        mgr.fire(CustomEvent("hello")).get()

        assertEquals("hello", received)
    }

    @Test
    fun `unregisterListeners removes handlers`() {
        val mgr = VelocityEventManagerShim(fakeServer)
        var count = 0

        val listener = object {
            @Subscribe
            fun onCustom(event: CustomEvent) { count++ }
        }

        mgr.register(listener, listener)
        mgr.fire(CustomEvent("a")).get()
        assertEquals(1, count)

        mgr.unregisterListeners(listener)
        mgr.fire(CustomEvent("b")).get()
        assertEquals(1, count) // should not increase
    }

    @Test
    fun `functional EventHandler register and fire`() {
        val mgr = VelocityEventManagerShim(fakeServer)
        var received: String? = null

        val plugin = Any()
        mgr.register(plugin, CustomEvent::class.java, PostOrder.NORMAL, EventHandler { e ->
            received = e.payload
        })

        mgr.fire(CustomEvent("world")).get()
        assertEquals("world", received)
    }

    @Test
    fun `fire returns event unchanged`() {
        val mgr = VelocityEventManagerShim(fakeServer)
        val event = CustomEvent("data")
        val result = mgr.fire(event).get()
        assertSame(event, result)
    }

    data class CustomEvent(val payload: String)
}
