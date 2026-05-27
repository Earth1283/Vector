package dev.vector.api

import java.util.UUID

interface VectorPlayer {
    val uuid: UUID
    val username: String
    fun disconnect(reason: String = "Disconnected")
    fun sendMessage(jsonText: String)
}
