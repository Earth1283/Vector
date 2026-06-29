package dev.vector.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

fun VectorPlayer.connectAsync(
    server: BackendServer,
    scope: CoroutineScope,
): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()
    scope.launch {
        try {
            future.complete(connect(server))
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
    }
    return future
}
