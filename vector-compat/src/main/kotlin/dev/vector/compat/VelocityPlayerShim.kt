package dev.vector.compat

import com.velocitypowered.api.network.ProtocolState
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder
import com.velocitypowered.api.proxy.player.PlayerSettings
import com.velocitypowered.api.proxy.player.SkinParts
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import com.velocitypowered.api.proxy.player.TabList
import com.velocitypowered.api.proxy.player.TabListEntry
import com.velocitypowered.api.proxy.player.ChatSession
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.api.util.ModInfo
import kotlinx.coroutines.future.future
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.net.InetSocketAddress
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class VelocityPlayerShim(
    val vectorPlayer: dev.vector.api.VectorPlayer,
    val vectorServer: dev.vector.api.ProxyServer,
) : Player {

    private val _identity: Identity = Identity.identity(vectorPlayer.uuid)
    private val playerSettings = VelocityPlayerSettings()
    private val tabList = VelocityTabList()

    // - Identified
    override fun identity(): Identity = _identity

    // - InboundConnection
    override fun getRemoteAddress(): InetSocketAddress =
        vectorPlayer.remoteAddress as? InetSocketAddress ?: InetSocketAddress(0)
    override fun getVirtualHost(): Optional<InetSocketAddress> = Optional.empty()
    override fun isActive(): Boolean = vectorPlayer.isConnected
    override fun getProtocolVersion(): ProtocolVersion =
        ProtocolVersion.getProtocolVersion(vectorPlayer.protocolVersion)
    override fun getProtocolState(): ProtocolState = ProtocolState.PLAY

    // - PermissionSubject
    override fun getPermissionValue(permission: String): Tristate = Tristate.UNDEFINED

    // - ChannelMessageSink
    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean = false
    override fun sendPluginMessage(identifier: ChannelIdentifier, encoder: PluginMessageEncoder): Boolean = false

    // - KeyIdentifiable
    override fun getIdentifiedKey() = null

    // - Player core
    override fun getUsername(): String = vectorPlayer.username
    override fun getUniqueId(): UUID = vectorPlayer.uuid

    override fun getEffectiveLocale(): Locale = Locale.getDefault()
    override fun setEffectiveLocale(locale: Locale) {}

    override fun getCurrentServer(): Optional<ServerConnection> {
        val server = vectorPlayer.currentServer ?: return Optional.empty()
        val registeredServer = VelocityRegisteredServerShim(server, vectorServer!!)
        return Optional.of(VelocityServerConnectionShim(this, registeredServer))
    }

    override fun getPlayerSettings(): PlayerSettings = playerSettings
    override fun hasSentPlayerSettings(): Boolean = true
    override fun getModInfo(): Optional<ModInfo> = Optional.empty()
    override fun getPing(): Long = -1L
    override fun isOnlineMode(): Boolean = true

    override fun createConnectionRequest(server: RegisteredServer): ConnectionRequestBuilder =
        VelocityConnectionRequestBuilder(this, server)

    override fun getGameProfileProperties(): List<GameProfile.Property> = emptyList()
    override fun setGameProfileProperties(properties: List<GameProfile.Property>) {}

    override fun getGameProfile(): GameProfile = GameProfile(vectorPlayer.uuid, vectorPlayer.username, emptyList())

    override fun clearPlayerListHeaderAndFooter() {}
    override fun getPlayerListHeader(): Component = Component.empty()
    override fun getPlayerListFooter(): Component = Component.empty()
    override fun getTabList(): TabList = tabList

    override fun disconnect(reason: Component) {
        val text = PlainTextComponentSerializer.plainText().serialize(reason)
        vectorPlayer.disconnect(text)
    }

    override fun spoofChatInput(input: String) {}

    override fun sendResourcePack(url: String) {}
    override fun sendResourcePack(url: String, hash: ByteArray) {}
    override fun sendResourcePackOffer(pack: ResourcePackInfo) {}
    override fun getAppliedResourcePack(): ResourcePackInfo? = null
    override fun getPendingResourcePack(): ResourcePackInfo? = null
    override fun getAppliedResourcePacks(): Collection<ResourcePackInfo> = emptyList()
    override fun getPendingResourcePacks(): Collection<ResourcePackInfo> = emptyList()

    override fun transferToHost(address: java.net.InetSocketAddress) {}
    override fun storeCookie(key: net.kyori.adventure.key.Key, data: ByteArray) {}
    override fun requestCookie(key: net.kyori.adventure.key.Key) {}
    override fun setServerLinks(links: List<com.velocitypowered.api.util.ServerLink>) {}

    override fun getClientBrand(): String? = null
    override fun addCustomChatCompletions(completions: Collection<String>) {}
    override fun removeCustomChatCompletions(completions: Collection<String>) {}
    override fun setCustomChatCompletions(completions: Collection<String>) {}

    // - Audience (sendMessage is the key one)
    override fun sendMessage(message: Component) {
        val json = GsonComponentSerializer.gson().serialize(message)
        vectorPlayer.sendMessage(json)
    }

    private class VelocityPlayerSettings : PlayerSettings {
        override fun getLocale(): Locale = Locale.US
        override fun getViewDistance(): Byte = 10
        override fun getChatMode(): PlayerSettings.ChatMode = PlayerSettings.ChatMode.SHOWN
        override fun hasChatColors(): Boolean = true
        override fun getSkinParts(): SkinParts = SkinParts(0x7F.toByte())
        override fun getMainHand(): PlayerSettings.MainHand = PlayerSettings.MainHand.RIGHT
        override fun isClientListingAllowed(): Boolean = true
    }

    private class VelocityTabList : TabList {
        private val entries = mutableMapOf<UUID, TabListEntry>()
        override fun setHeaderAndFooter(header: Component, footer: Component) {}
        override fun clearHeaderAndFooter() {}
        override fun addEntry(entry: TabListEntry) { entries[entry.profile.id] = entry }
        override fun removeEntry(uuid: UUID): Optional<TabListEntry> = Optional.ofNullable(entries.remove(uuid))
        override fun containsEntry(uuid: UUID): Boolean = entries.containsKey(uuid)
        override fun getEntry(uuid: UUID): Optional<TabListEntry> = Optional.ofNullable(entries[uuid])
        override fun getEntries(): MutableCollection<TabListEntry> = entries.values
        override fun clearAll() { entries.clear() }
        override fun buildEntry(profile: GameProfile, displayName: Component?, latency: Int, gameMode: Int, chatSession: ChatSession?, listed: Boolean): TabListEntry {
            return VelocityTabListEntry(this, profile, displayName, latency, gameMode, chatSession, listed)
        }
    }

    private class VelocityTabListEntry(
        private val list: TabList,
        private val profile: GameProfile,
        private var displayName: Component?,
        private var latency: Int,
        private var gameMode: Int,
        private val chatSession: ChatSession?,
        private var listed: Boolean
    ) : TabListEntry {
        override fun getTabList(): TabList = list
        override fun getProfile(): GameProfile = profile
        override fun getDisplayNameComponent(): Optional<Component> = Optional.ofNullable(displayName)
        override fun setDisplayName(displayName: Component?): TabListEntry { this.displayName = displayName; return this }
        override fun getLatency(): Int = latency
        override fun setLatency(latency: Int): TabListEntry { this.latency = latency; return this }
        override fun getGameMode(): Int = gameMode
        override fun setGameMode(gameMode: Int): TabListEntry { this.gameMode = gameMode; return this }
        override fun getChatSession(): ChatSession? = chatSession
        override fun isListed(): Boolean = listed
        override fun setListed(listed: Boolean): TabListEntry { this.listed = listed; return this }
        override fun getIdentifiedKey(): com.velocitypowered.api.proxy.crypto.IdentifiedKey? = null
    }

    private class VelocityConnectionRequestBuilder(
        private val player: VelocityPlayerShim,
        private val server: RegisteredServer
    ) : ConnectionRequestBuilder {
        override fun getServer(): RegisteredServer = server
        override fun connect(): CompletableFuture<ConnectionRequestBuilder.Result> {
            val vectorServer = (server as? VelocityRegisteredServerShim)?.backendServer ?: return CompletableFuture.failedFuture(IllegalArgumentException("Invalid server"))
            return player.vectorServer.coroutineScope.future {
                val success = player.vectorPlayer.connect(vectorServer)
                if (success) VelocityConnectionResult(ConnectionRequestBuilder.Status.SUCCESS, server)
                else VelocityConnectionResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, server)
            }
        }

        override fun connectWithIndication(): CompletableFuture<Boolean> {
            return connect().thenApply { it.status == ConnectionRequestBuilder.Status.SUCCESS }
        }

        override fun fireAndForget(): Unit { connect() }
    }

    private class VelocityConnectionResult(
        private val status: ConnectionRequestBuilder.Status,
        private val server: RegisteredServer
    ) : ConnectionRequestBuilder.Result {
        override fun getStatus(): ConnectionRequestBuilder.Status = status
        override fun getReasonComponent(): Optional<Component> = Optional.empty()
        override fun getAttemptedConnection(): RegisteredServer = server
    }
}
