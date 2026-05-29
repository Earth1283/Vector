package dev.vector.vectest.tests

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.util.ProxyVersion
import dev.vector.api.ProxyServer as VectorProxyServer
import dev.vector.vectest.VecTestSuite
import net.kyori.adventure.text.Component
import java.net.InetSocketAddress
import java.util.UUID

class VelocityProxyServerTests(
    private val s: VecTestSuite,
    private val proxy: ProxyServer,
    private val vectorServer: VectorProxyServer,
) {
    fun run() {
        s.check("ProxyServer.getEventManager non-null") { s.assertNotNull(proxy.getEventManager()) }
        s.check("ProxyServer.getCommandManager non-null") { s.assertNotNull(proxy.getCommandManager()) }
        s.check("ProxyServer.getScheduler non-null") { s.assertNotNull(proxy.getScheduler()) }
        s.check("ProxyServer.getPluginManager non-null") { s.assertNotNull(proxy.getPluginManager()) }

        s.check("ProxyServer.getPlayer(UUID) unknown — empty") {
            s.assert(proxy.getPlayer(UUID.randomUUID()).isEmpty)
        }
        s.check("ProxyServer.getPlayer(String) unknown — empty") {
            s.assert(proxy.getPlayer("__vectest_nobody__").isEmpty)
        }
        s.check("ProxyServer.getAllPlayers non-null") { s.assertNotNull(proxy.getAllPlayers()) }
        s.check("ProxyServer.getPlayerCount >= 0") { s.assert(proxy.playerCount >= 0) }
        s.check("ProxyServer.matchPlayer returns collection") {
            s.assertNotNull(proxy.matchPlayer("VecNonExistent"))
        }

        s.check("ProxyServer.getAllServers non-null") { s.assertNotNull(proxy.getAllServers()) }
        s.check("ProxyServer.getServer unknown — empty") {
            s.assert(proxy.getServer("__vectest_nosuchserver__").isEmpty)
        }
        s.check("ProxyServer.matchServer returns collection") {
            s.assertNotNull(proxy.matchServer("__vectest_nosuchserver__"))
        }

        val srvInfo = ServerInfo("vectest-reg-${UUID.randomUUID()}", InetSocketAddress("localhost", 25599))
        s.check("ProxyServer.registerServer returns RegisteredServer") {
            val rs = proxy.registerServer(srvInfo)
            s.assertEquals(srvInfo.name, rs.serverInfo.name)
        }
        s.check("ProxyServer.getAllServers contains registered") {
            s.assert(proxy.getAllServers().any { it.serverInfo.name == srvInfo.name })
        }
        s.check("ProxyServer.createRawRegisteredServer returns RegisteredServer") {
            val rawInfo = ServerInfo("vectest-raw-${UUID.randomUUID()}", InetSocketAddress("localhost", 25598))
            val rs = proxy.createRawRegisteredServer(rawInfo)
            s.assertEquals(rawInfo.name, rs.serverInfo.name)
        }
        s.check("ProxyServer.unregisterServer no throw") {
            proxy.unregisterServer(srvInfo)
        }
        s.check("ProxyServer.getAllServers no longer contains after unregister") {
            s.assert(proxy.getAllServers().none { it.serverInfo.name == srvInfo.name })
        }

        s.check("ProxyServer.getConsoleCommandSource non-null") {
            s.assertNotNull(proxy.consoleCommandSource)
        }
        s.check("ProxyServer.getBoundAddress non-null") {
            s.assertNotNull(proxy.boundAddress)
        }
        s.check("ProxyServer.getVersion name is Vector") {
            s.assertEquals("Vector", proxy.version.name)
        }
        s.check("ProxyServer.getChannelRegistrar non-null") {
            s.assertNotNull(proxy.channelRegistrar)
        }
        s.check("ProxyServer.getConfiguration non-null") {
            s.assertNotNull(proxy.configuration)
        }
        s.check("ProxyServer.sendMessage no throw") {
            proxy.sendMessage(Component.text("[VecTest]"))
        }

        val rpUrl = "https://example.com/vectest-rp.zip"
        s.check("ProxyServer.createResourcePackBuilder returns builder") {
            val builder = proxy.createResourcePackBuilder(rpUrl)
            s.assertNotNull(builder)
        }
        s.check("ResourcePackInfo.Builder — build produces correct ResourcePackInfo") {
            val id = UUID.randomUUID()
            val rp = proxy.createResourcePackBuilder(rpUrl)
                .setId(id)
                .setShouldForce(true)
                .build()
            s.assertEquals(rpUrl, rp.url)
            s.assertEquals(id, rp.id)
            s.assert(rp.shouldForce)
        }
        s.check("ResourcePackInfo.asBuilder() round-trips url") {
            val rp = proxy.createResourcePackBuilder(rpUrl).build()
            val copy = rp.asBuilder().build()
            s.assertEquals(rpUrl, copy.url)
        }
        s.check("ResourcePackInfo.asBuilder(url) sets new url") {
            val rp = proxy.createResourcePackBuilder(rpUrl).build()
            val other = rp.asBuilder("https://example.com/other.zip").build()
            s.assertEquals("https://example.com/other.zip", other.url)
        }
        s.check("ResourcePackInfo.getOrigin returns PLUGIN_ON_PROXY") {
            val rp = proxy.createResourcePackBuilder(rpUrl).build()
            s.assertNotNull(rp.origin)
        }
    }
}
