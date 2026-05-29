package dev.vector.vectest

import dev.vector.api.BackendServer
import dev.vector.api.VectorPlayer
import java.util.UUID

class MockVectorPlayer(
    override val uuid: UUID = UUID.randomUUID(),
    override val username: String = "VecTestPlayer",
) : VectorPlayer {
    override val currentServer: BackendServer? = null
    val disconnects = mutableListOf<String>()
    val messages = mutableListOf<String>()

    override fun disconnect(reason: String) { disconnects += reason }
    override fun sendMessage(jsonText: String) { messages += jsonText }
    override suspend fun connect(server: BackendServer): Boolean = false
}
