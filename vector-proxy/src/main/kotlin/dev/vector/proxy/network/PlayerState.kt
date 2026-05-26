package dev.vector.proxy.network

sealed class PlayerState {
    object Handshaking : PlayerState()
    object LoggingIn : PlayerState()
    object Authenticating : PlayerState()
    data class InServer(val serverName: String) : PlayerState()
    object Disconnecting : PlayerState()

    override fun toString(): String = when (this) {
        is Handshaking -> "Handshaking"
        is LoggingIn -> "LoggingIn"
        is Authenticating -> "Authenticating"
        is InServer -> "InServer($serverName)"
        is Disconnecting -> "Disconnecting"
    }

    fun transition(next: PlayerState): PlayerState {
        val allowed = when (this) {
            is Handshaking -> next is LoggingIn || next is Disconnecting
            is LoggingIn -> next is Authenticating || next is Disconnecting
            is Authenticating -> next is InServer || next is Disconnecting
            is InServer -> next is Disconnecting
            is Disconnecting -> false
        }
        check(allowed) { "Illegal player state transition: $this → $next" }
        return next
    }
}
