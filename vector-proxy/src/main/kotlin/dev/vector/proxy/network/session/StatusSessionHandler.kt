package dev.vector.proxy.network.session

import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.status.PingRequestPacket
import dev.vector.proxy.protocol.packet.status.PingResponsePacket
import dev.vector.proxy.protocol.packet.status.StatusRequestPacket
import dev.vector.proxy.protocol.packet.status.StatusResponsePacket

class StatusSessionHandler(private val connection: MinecraftConnection) : SessionHandler {

    override fun handle(packet: StatusRequestPacket): Boolean {
        val version = connection.protocolVersion
            .takeUnless { it == ProtocolVersion.UNKNOWN } ?: ProtocolVersion.MAXIMUM
        val json = connection.server?.buildStatusJson(version)
            ?: fallbackJson(version)
        connection.write(StatusResponsePacket(json))
        return true
    }

    override fun handle(packet: PingRequestPacket): Boolean {
        connection.closeWith(PingResponsePacket(packet.payload))
        return true
    }

    override fun exception(cause: Throwable) {
        connection.close()
    }

    private fun fallbackJson(version: ProtocolVersion) =
        """{"version":{"name":"${version.versionString}","protocol":${version.protocol}},"players":{"max":0,"online":0,"sample":[]},"description":{"text":"Vector"}}"""
}
