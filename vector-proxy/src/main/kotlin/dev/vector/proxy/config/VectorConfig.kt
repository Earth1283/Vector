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
        @SerialName("none")       NONE,
        @SerialName("legacy")     LEGACY,
        @SerialName("bungeeguard") BUNGEEGUARD,
        @SerialName("modern")     MODERN,
    }

    @Serializable
    data class CompressionConfig(
        val threshold: Int = 256,
    )

    companion object {
        private val DEFAULT_TOML = """
            bind = "0.0.0.0:25565"

            [servers]
            lobby = "localhost:25577"

            [routing]
            try = ["lobby"]

            [forwarding]
            mode = "none"
            secret = ""

            [compression]
            threshold = 256
        """.trimIndent()

        fun load(path: Path = Path.of("vector.toml")): VectorConfig {
            if (!path.exists()) {
                path.writeText(DEFAULT_TOML)
                return Toml.decodeFromString(DEFAULT_TOML)
            }
            return Toml.decodeFromString(path.readText())
        }
    }
}
