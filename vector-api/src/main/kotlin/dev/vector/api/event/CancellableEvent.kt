package dev.vector.api.event

open class CancellableEvent : VectorEvent() {
    var isCancelled: Boolean = false
}
