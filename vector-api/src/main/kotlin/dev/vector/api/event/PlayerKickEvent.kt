package dev.vector.api.event

import dev.vector.api.VectorPlayer

class PlayerKickEvent(
    val player: VectorPlayer,
    val reason: String,
) : VectorEvent()
