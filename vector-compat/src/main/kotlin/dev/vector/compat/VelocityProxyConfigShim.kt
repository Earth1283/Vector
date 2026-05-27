package dev.vector.compat

import com.velocitypowered.api.proxy.config.ProxyConfig
import com.velocitypowered.api.util.Favicon
import net.kyori.adventure.text.Component
import java.util.Optional

class VelocityProxyConfigShim : ProxyConfig {
    override fun isQueryEnabled(): Boolean = false
    override fun getQueryPort(): Int = 25565
    override fun getQueryMap(): String = "Vector"
    override fun shouldQueryShowPlugins(): Boolean = false
    override fun getMotd(): Component = Component.empty()
    override fun getShowMaxPlayers(): Int = 0
    override fun isOnlineMode(): Boolean = true
    override fun shouldPreventClientProxyConnections(): Boolean = false
    override fun getServers(): Map<String, String> = emptyMap()
    override fun getAttemptConnectionOrder(): List<String> = emptyList()
    override fun getForcedHosts(): Map<String, List<String>> = emptyMap()
    override fun getCompressionThreshold(): Int = 256
    override fun getCompressionLevel(): Int = -1
    override fun getLoginRatelimit(): Int = 0
    override fun getFavicon(): Optional<Favicon> = Optional.empty()
    override fun isAnnounceForge(): Boolean = false
    override fun getConnectTimeout(): Int = 5000
    override fun getReadTimeout(): Int = 30000
}
