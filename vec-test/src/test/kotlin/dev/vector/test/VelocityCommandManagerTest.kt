package dev.vector.test

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import dev.vector.api.ProxyServer
import dev.vector.api.VectorPlayer
import dev.vector.api.event.EventBus
import dev.vector.api.storage.StorageBackend
import dev.vector.compat.VelocityCommandManagerShim
import dev.vector.compat.VelocityCommandManagerShimDelegator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class VelocityCommandManagerTest {

    private val fakeServer = object : ProxyServer {
        val registeredCommands = mutableMapOf<String, Pair<String, suspend (List<String>) -> Unit>>()
        
        override val version = "test"
        override val eventBus: EventBus get() = throw UnsupportedOperationException()
        override val storage: StorageBackend get() = throw UnsupportedOperationException()
        override val players: Collection<VectorPlayer> = emptyList()
        override val servers: Collection<dev.vector.api.BackendServer> = emptyList()
        override val coroutineScope: CoroutineScope get() = GlobalScope
        override fun getPlayer(uuid: UUID): VectorPlayer? = null
        override fun getPlayer(username: String): VectorPlayer? = null
        
        override fun registerCommand(
            name: String,
            pluginId: String,
            handler: suspend (List<String>) -> Unit,
            completer: (List<String>) -> List<String>
        ) {
            registeredCommands[name] = pluginId to handler
        }
        
        override fun unregisterCommand(name: String) {
            registeredCommands.remove(name)
        }

        override fun unregisterCommands(pluginId: String) {
            registeredCommands.entries.removeIf { it.value.first == pluginId }
        }

        override fun registerServer(name: String, address: java.net.InetSocketAddress): dev.vector.api.BackendServer {
            throw UnsupportedOperationException()
        }

        override fun unregisterServer(name: String) {
            throw UnsupportedOperationException()
        }
    }

    @Test
    fun `register simple command with aliases`() {
        val mgr = VelocityCommandManagerShim(fakeServer)
        var executed = false
        
        val command = object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                executed = true
                assertEquals("test", invocation.alias())
                assertArrayEquals(arrayOf("arg1", "arg2"), invocation.arguments())
            }
        }
        
        val meta = mgr.metaBuilder("test")
            .aliases("t", "testing")
            .build()
            
        mgr.register(meta, command)
        
        assertTrue(fakeServer.registeredCommands.containsKey("test"))
        assertTrue(fakeServer.registeredCommands.containsKey("t"))
        assertTrue(fakeServer.registeredCommands.containsKey("testing"))
        
        // Simulate execution
        val handler = fakeServer.registeredCommands["test"]!!.second
        kotlinx.coroutines.runBlocking {
            handler(listOf("arg1", "arg2"))
        }
        
        assertTrue(executed)
    }

    @Test
    fun `delegator uses correct plugin id`() {
        val mgr = VelocityCommandManagerShim(fakeServer)
        val delegator = VelocityCommandManagerShimDelegator(mgr, "my-plugin")
        
        val command = object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {}
        }
        
        delegator.register(delegator.metaBuilder("cmd").build(), command)
        
        assertEquals("my-plugin", fakeServer.registeredCommands["cmd"]?.first)
    }
}
