package dev.vector.api.event

import dev.vector.api.VectorPlayer

class PlayerChatEvent(
    val player: VectorPlayer,
    val message: String,
) : CancellableEvent()
