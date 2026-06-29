package dev.vector.api.kotlin

import dev.vector.api.event.EventPriority
import dev.vector.api.event.VectorEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

inline fun <reified T : VectorEvent> VectorPluginScope.eventFlow(
    priority: EventPriority = EventPriority.NORMAL,
): Flow<T> {
    val channel = Channel<T>(Channel.UNLIMITED)
    on<T>(priority) { event -> channel.send(event) }
    launch { try { awaitCancellation() } finally { channel.close() } }
    return channel.receiveAsFlow()
}
