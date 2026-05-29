package dev.vector.vectest.tests

import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.player.PlayerSettings
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.util.GameProfile
import dev.vector.api.BackendServer
import dev.vector.api.ProxyServer as VectorProxyServer
import dev.vector.compat.VelocityPlayerShim
import dev.vector.compat.VelocityRegisteredServerShim
import dev.vector.vectest.MockVectorPlayer
import dev.vector.vectest.VecTestSuite
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import java.net.InetSocketAddress
import java.util.UUID

class VelocityPlayerTests(
    private val s: VecTestSuite,
    private val proxy: ProxyServer,
    private val vectorServer: VectorProxyServer,
) {
    fun run() {
        val mock = MockVectorPlayer()
        val player = VelocityPlayerShim(mock, vectorServer)

        // - Identified
        s.check("Player.identity().uuid matches") {
            s.assertEquals(mock.uuid, player.identity().uuid())
        }

        // - InboundConnection
        s.check("Player.getRemoteAddress non-null") { s.assertNotNull(player.remoteAddress) }
        s.check("Player.getVirtualHost empty") { s.assert(player.virtualHost.isEmpty) }
        s.check("Player.isActive true") { s.assert(player.isActive) }
        s.check("Player.getProtocolVersion non-null") { s.assertNotNull(player.protocolVersion) }
        s.check("Player.getProtocolState non-null") { s.assertNotNull(player.protocolState) }

        // - PermissionSubject
        s.check("Player.getPermissionValue UNDEFINED") {
            s.assertEquals(Tristate.UNDEFINED, player.getPermissionValue("test.permission"))
        }

        // - ChannelMessageSink
        s.check("Player.sendPluginMessage(id, bytes) false") {
            val id = com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier.create("vectest", "test")
            s.assert(!player.sendPluginMessage(id, ByteArray(0)))
        }

        // - KeyIdentifiable
        s.check("Player.getIdentifiedKey null") { s.assertNull(player.identifiedKey) }

        // - Player core
        s.check("Player.getUsername correct") { s.assertEquals(mock.username, player.username) }
        s.check("Player.getUniqueId correct") { s.assertEquals(mock.uuid, player.uniqueId) }
        s.check("Player.getEffectiveLocale non-null") { s.assertNotNull(player.effectiveLocale) }
        s.check("Player.setEffectiveLocale no throw") { player.setEffectiveLocale(java.util.Locale.US) }
        s.check("Player.getCurrentServer empty (no server)") { s.assert(player.currentServer.isEmpty) }

        // - PlayerSettings
        s.check("Player.hasSentPlayerSettings true") { s.assert(player.hasSentPlayerSettings()) }
        s.check("Player.getPlayerSettings non-null") { s.assertNotNull(player.playerSettings) }
        s.check("PlayerSettings.getLocale non-null") { s.assertNotNull(player.playerSettings.locale) }
        s.check("PlayerSettings.getViewDistance 10") {
            s.assertEquals(10.toByte(), player.playerSettings.viewDistance)
        }
        s.check("PlayerSettings.getChatMode SHOWN") {
            s.assertEquals(PlayerSettings.ChatMode.SHOWN, player.playerSettings.chatMode)
        }
        s.check("PlayerSettings.hasChatColors true") { s.assert(player.playerSettings.hasChatColors()) }
        s.check("PlayerSettings.getSkinParts non-null") { s.assertNotNull(player.playerSettings.skinParts) }
        s.check("PlayerSettings.getMainHand RIGHT") {
            s.assertEquals(PlayerSettings.MainHand.RIGHT, player.playerSettings.mainHand)
        }
        s.check("PlayerSettings.isClientListingAllowed true") {
            s.assert(player.playerSettings.isClientListingAllowed)
        }

        s.check("Player.getModInfo empty") { s.assert(player.modInfo.isEmpty) }
        s.check("Player.getPing -1") { s.assertEquals(-1L, player.ping) }
        s.check("Player.isOnlineMode true") { s.assert(player.isOnlineMode) }

        s.check("Player.getGameProfileProperties empty") {
            s.assert(player.gameProfileProperties.isEmpty())
        }
        s.check("Player.setGameProfileProperties no throw") {
            player.setGameProfileProperties(emptyList())
        }
        s.check("Player.getGameProfile.id matches") {
            s.assertEquals(mock.uuid, player.gameProfile.id)
        }
        s.check("Player.getGameProfile.name matches") {
            s.assertEquals(mock.username, player.gameProfile.name)
        }

        s.check("Player.clearPlayerListHeaderAndFooter no throw") { player.clearPlayerListHeaderAndFooter() }
        s.check("Player.getPlayerListHeader Component.empty") {
            s.assertEquals(Component.empty(), player.playerListHeader)
        }
        s.check("Player.getPlayerListFooter Component.empty") {
            s.assertEquals(Component.empty(), player.playerListFooter)
        }

        // - TabList
        val tabList = player.tabList
        s.check("Player.getTabList non-null") { s.assertNotNull(tabList) }
        s.check("TabList.setHeaderAndFooter no throw") {
            tabList.setHeaderAndFooter(Component.text("H"), Component.text("F"))
        }
        s.check("TabList.clearHeaderAndFooter no throw") { tabList.clearHeaderAndFooter() }

        val entryUuid = UUID.randomUUID()
        val profile = GameProfile(entryUuid, "VecTabEntry", emptyList())
        val entry = tabList.buildEntry(profile, Component.text("VecEntry"), 20, 0, null, true)
        s.check("TabList.buildEntry non-null") { s.assertNotNull(entry) }
        s.check("TabListEntry.getProfile matches") { s.assertEquals(entryUuid, entry.profile.id) }
        s.check("TabListEntry.getLatency 20") { s.assertEquals(20, entry.latency) }
        s.check("TabListEntry.getGameMode 0") { s.assertEquals(0, entry.gameMode) }
        s.check("TabListEntry.isListed true") { s.assert(entry.isListed) }
        s.check("TabListEntry.getDisplayNameComponent present") { s.assert(entry.displayNameComponent.isPresent) }
        s.check("TabListEntry.setDisplayName no throw") { entry.setDisplayName(null) }
        s.check("TabListEntry.setLatency no throw") { entry.setLatency(50) }
        s.check("TabListEntry.setGameMode no throw") { entry.setGameMode(1) }
        s.check("TabListEntry.setListed no throw") { entry.setListed(false) }
        s.check("TabListEntry.getChatSession null") { s.assertNull(entry.chatSession) }
        s.check("TabListEntry.getIdentifiedKey null") { s.assertNull(entry.identifiedKey) }
        s.check("TabListEntry.getTabList same instance") { s.assertEquals(tabList, entry.tabList) }
        tabList.addEntry(tabList.buildEntry(profile, null, 0, 0, null, true))
        s.check("TabList.containsEntry true after add") { s.assert(tabList.containsEntry(entryUuid)) }
        s.check("TabList.getEntry present") { s.assert(tabList.getEntry(entryUuid).isPresent) }
        s.check("TabList.getEntries non-empty") { s.assert(tabList.entries.isNotEmpty()) }
        s.check("TabList.removeEntry returns entry") {
            s.assert(tabList.removeEntry(entryUuid).isPresent)
        }
        s.check("TabList.containsEntry false after remove") { s.assert(!tabList.containsEntry(entryUuid)) }
        s.check("TabList.clearAll empties entries") { tabList.clearAll(); s.assert(tabList.entries.isEmpty()) }

        // - Disconnect
        val mock2 = MockVectorPlayer()
        val player2 = VelocityPlayerShim(mock2, vectorServer)
        s.check("Player.disconnect(Component) calls VectorPlayer.disconnect") {
            player2.disconnect(Component.text("VecTest kick"))
            s.assert(mock2.disconnects.isNotEmpty())
        }

        // - sendMessage
        val mock3 = MockVectorPlayer()
        val player3 = VelocityPlayerShim(mock3, vectorServer)
        s.check("Player.sendMessage(Component) calls VectorPlayer.sendMessage with JSON") {
            player3.sendMessage(Component.text("Hello VecTest"))
            s.assert(mock3.messages.any { it.contains("Hello VecTest") })
        }

        // - Resource pack stubs
        s.check("Player.spoofChatInput no throw") { player.spoofChatInput("hello") }
        s.check("Player.sendResourcePack(url) no throw") {
            player.sendResourcePack("https://example.com/rp.zip")
        }
        s.check("Player.sendResourcePack(url, hash) no throw") {
            player.sendResourcePack("https://example.com/rp.zip", ByteArray(20))
        }
        s.check("Player.getAppliedResourcePack null") { s.assertNull(player.appliedResourcePack) }
        s.check("Player.getPendingResourcePack null") { s.assertNull(player.pendingResourcePack) }
        s.check("Player.getAppliedResourcePacks empty") { s.assert(player.appliedResourcePacks.isEmpty()) }
        s.check("Player.getPendingResourcePacks empty") { s.assert(player.pendingResourcePacks.isEmpty()) }

        // - Modern API stubs
        s.check("Player.transferToHost no throw") {
            player.transferToHost(InetSocketAddress("localhost", 25565))
        }
        s.check("Player.storeCookie no throw") {
            player.storeCookie(Key.key("vectest", "cookie"), ByteArray(4))
        }
        s.check("Player.requestCookie no throw") {
            player.requestCookie(Key.key("vectest", "cookie"))
        }
        s.check("Player.setServerLinks no throw") { player.setServerLinks(emptyList()) }
        s.check("Player.getClientBrand null") { s.assertNull(player.clientBrand) }
        s.check("Player.addCustomChatCompletions no throw") {
            player.addCustomChatCompletions(listOf("vectest-completion"))
        }
        s.check("Player.removeCustomChatCompletions no throw") {
            player.removeCustomChatCompletions(listOf("vectest-completion"))
        }
        s.check("Player.setCustomChatCompletions no throw") {
            player.setCustomChatCompletions(emptyList())
        }

        // - createConnectionRequest
        val mockBackend = object : BackendServer {
            override val name = "vectest-conn-srv"
            override val address = InetSocketAddress("localhost", 25599)
        }
        val regServer = VelocityRegisteredServerShim(mockBackend, vectorServer)
        s.check("Player.createConnectionRequest non-null") {
            val builder = player.createConnectionRequest(regServer)
            s.assertNotNull(builder)
        }
        s.check("Player.createConnectionRequest.getServer correct") {
            val builder = player.createConnectionRequest(regServer)
            s.assertEquals(regServer, builder.server)
        }
    }
}
