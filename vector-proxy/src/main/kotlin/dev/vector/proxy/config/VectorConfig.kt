package dev.vector.proxy.config

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class VectorConfig(
    val bind: String = "0.0.0.0:25565",
    val servers: Map<String, String> = mapOf("lobby" to "localhost:25577"),
    val routing: RoutingConfig = RoutingConfig(),
    val forwarding: ForwardingConfig = ForwardingConfig(),
    val compression: CompressionConfig = CompressionConfig(),
    @SerialName("connection-limits")
    val connectionLimits: ConnectionLimitsConfig = ConnectionLimitsConfig(),
    val storage: StorageConfig = StorageConfig(),
    val motd: MotdConfig = MotdConfig(),
    val threading: ThreadingConfig = ThreadingConfig(),
    val management: ManagementConfig = ManagementConfig(),
    @SerialName("player-experience")
    val playerExperience: PlayerExperienceConfig = PlayerExperienceConfig(),
    val limbo: LimboConfig = LimboConfig(),
    val console: ConsoleConfig = ConsoleConfig(),
) {
    @Serializable
    data class RoutingConfig(
        @SerialName("try")
        val tryServers: List<String> = listOf("lobby"),
    )

    @Serializable
    data class ForwardingConfig(
        val mode: ForwardingMode = ForwardingMode.NONE,
        val secret: String = "",
    )

    @Serializable
    enum class ForwardingMode {
        @SerialName("none")        NONE,
        @SerialName("legacy")      LEGACY,
        @SerialName("bungeeguard") BUNGEEGUARD,
        @SerialName("modern")      MODERN,
    }

    @Serializable
    data class CompressionConfig(
        val threshold: Int = 256,
    )

    @Serializable
    data class ConnectionLimitsConfig(
        // Max concurrent connections from a single source IP. 0 = unlimited.
        @SerialName("max-per-ip") val maxPerIp: Int = 16,
        // Max concurrent connections proxy-wide. 0 = unlimited.
        @SerialName("max-total")  val maxTotal: Int = 0,
    )

    @Serializable
    data class StorageConfig(
        val file: String = "data/vector.db",
    )

    @Serializable
    data class MotdConfig(
        val description: String = "A Vector Proxy",
        val favicon: String = "server-icon.png",
    )

    @Serializable
    data class ThreadingConfig(
        @SerialName("boss-threads")   val bossThreads: Int = 1,
        @SerialName("worker-threads") val workerThreads: Int = 0,
    )

    @Serializable
    data class ManagementConfig(
        val maintenance: MaintenanceConfig = MaintenanceConfig(),
        @SerialName("forced-hosts")
        val forcedHosts: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class MaintenanceConfig(
        val enabled: Boolean = false,
        val message: String = "Server is under maintenance.",
    )

    @Serializable
    data class PlayerExperienceConfig(
        @SerialName("player-count-modifier")         val playerCountModifier: Double = 1.0,
        @SerialName("player-count-minimum")          val playerCountMinimum: Int = 0,
        @SerialName("player-count-maximum-padding")  val playerCountMaximumPadding: Int = 0,
        @SerialName("hide-player-count")             val hidePlayerCount: Boolean = false,
        @SerialName("backend-disconnect")
        val backendDisconnect: BackendDisconnectConfig = BackendDisconnectConfig(),
    )

    @Serializable
    data class BackendDisconnectConfig(
        val action: BackendDisconnectAction = BackendDisconnectAction.KICK,
        @SerialName("fallback-message")
        val fallbackMessage: String = "Lost connection to server.",
    )

    @Serializable
    enum class BackendDisconnectAction {
        @SerialName("kick")             KICK,
        @SerialName("send-to-fallback") SEND_TO_FALLBACK,
    }

    @Serializable
    data class LimboConfig(
        @SerialName("unclaimed-action")
        val unclaimedAction: LimboAction = LimboAction.KICK,
        @SerialName("unclaimed-message")
        val unclaimedMessage: String = "No server available.",
        @SerialName("max-hold-duration")
        val maxHoldDuration: Long = 120L,
    )

    @Serializable
    enum class LimboAction {
        @SerialName("kick") KICK,
        @SerialName("hold") HOLD,
    }

    @Serializable
    data class ConsoleConfig(
        @SerialName("simple-prompt") val simplePrompt: Boolean = false,
    )

    companion object {
        private val DEFAULT_TOML: String =
            VectorConfig::class.java.getResourceAsStream("/default-vector.toml")!!
                .bufferedReader().readText()

        fun load(path: Path = Path.of("vector.toml")): VectorConfig {
            if (!path.exists()) {
                path.writeText(DEFAULT_TOML)
                return Toml.decodeFromString(DEFAULT_TOML)
            }
            return Toml.decodeFromString(path.readText())
        }
    }
}
