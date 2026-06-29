package dev.vector.api.event

import dev.vector.api.BackendServer
import dev.vector.api.VectorPlayer

class ServerSwitchEvent(
    val player: VectorPlayer,
    val previousServer: BackendServer?,
    val targetServer: BackendServer,
) : CancellableEvent()
