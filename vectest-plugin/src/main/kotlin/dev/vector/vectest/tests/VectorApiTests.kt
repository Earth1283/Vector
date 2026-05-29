package dev.vector.vectest.tests

import dev.vector.api.BackendServer
import dev.vector.api.ProxyServer
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.vectest.MockVectorPlayer
import dev.vector.vectest.VecTestSuite
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.UUID

class VectorApiTests(
    private val s: VecTestSuite,
    private val server: ProxyServer,
) {
    fun run() {
        s.check("ProxyServer.version non-null/non-empty") {
            s.assert(server.version.isNotBlank())
        }
        s.check("ProxyServer.eventBus non-null") { s.assertNotNull(server.eventBus) }
        s.check("ProxyServer.storage non-null") { s.assertNotNull(server.storage) }
        s.check("ProxyServer.players returns Collection") { s.assertNotNull(server.players) }
        s.check("ProxyServer.servers returns Collection") { s.assertNotNull(server.servers) }
        s.check("ProxyServer.coroutineScope non-null") { s.assertNotNull(server.coroutineScope) }
        s.check("ProxyServer.getPlayer(UUID) — unknown returns null") {
            s.assertNull(server.getPlayer(UUID.randomUUID()))
        }
        s.check("ProxyServer.getPlayer(String) — unknown returns null") {
            s.assertNull(server.getPlayer("__vectest_nonexistent_1234__"))
        }

        val registeredName = "vectest-api-srv-${UUID.randomUUID()}"
        var registered: BackendServer? = null
        s.check("ProxyServer.registerServer returns BackendServer") {
            registered = server.registerServer(registeredName, InetSocketAddress("localhost", 25599))
            s.assertNotNull(registered)
            s.assertEquals(registeredName, registered!!.name)
        }
        s.check("ProxyServer.servers contains registered server") {
            s.assert(server.servers.any { it.name == registeredName })
        }
        s.check("ProxyServer.unregisterServer no throw") {
            server.unregisterServer(registeredName)
        }
        s.check("ProxyServer.servers no longer contains unregistered server") {
            s.assert(server.servers.none { it.name == registeredName })
        }

        s.check("ProxyServer.registerCommand no throw") {
            server.registerCommand("vectest-native-cmd", "vectest", {})
        }
        s.check("ProxyServer.unregisterCommands no throw") {
            server.unregisterCommands("vectest")
        }
        s.check("ProxyServer.eventBus.fire no throw") {
            runBlocking { server.eventBus.fire(ProxyInitializeEvent()) }
        }

        val mock = MockVectorPlayer()
        s.check("MockVectorPlayer.uuid non-null") { s.assertNotNull(mock.uuid) }
        s.check("MockVectorPlayer.username correct") { s.assertEquals("VecTestPlayer", mock.username) }
        s.check("MockVectorPlayer.currentServer null") { s.assertNull(mock.currentServer) }
        s.check("MockVectorPlayer.disconnect records call") {
            mock.disconnect("test-reason")
            s.assert(mock.disconnects.contains("test-reason"))
        }
        s.check("MockVectorPlayer.sendMessage records call") {
            mock.sendMessage("{\"text\":\"hello\"}")
            s.assert(mock.messages.any { it.contains("hello") })
        }
    }
}
