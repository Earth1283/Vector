package dev.vector.vectest

import dev.vector.api.BackendServer
import dev.vector.api.VectorPlayer
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID

class MockVectorPlayer(
    override val uuid: UUID = UUID.randomUUID(),
    override val username: String = "VecTestPlayer",
) : VectorPlayer {
    override val currentServer: BackendServer? = null
    override val remoteAddress: SocketAddress = InetSocketAddress("127.0.0.1", 0)
    override val protocolVersion: Int = 769
    override val isConnected: Boolean = true
    val disconnects = mutableListOf<String>()
    val messages = mutableListOf<String>()

    override fun disconnect(reason: String) { disconnects += reason }
    override fun sendMessage(jsonText: String) { messages += jsonText }
    override suspend fun connect(server: BackendServer): Boolean = false
}
