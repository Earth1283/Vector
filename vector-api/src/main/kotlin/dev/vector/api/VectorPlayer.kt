package dev.vector.api

import java.net.SocketAddress
import java.util.UUID

interface VectorPlayer {
    val uuid: UUID
    val username: String
    val currentServer: BackendServer?
    val remoteAddress: SocketAddress
    val protocolVersion: Int
    val isConnected: Boolean
    fun disconnect(reason: String = "Disconnected")
    fun sendMessage(jsonText: String)
    suspend fun connect(server: BackendServer): Boolean
}
