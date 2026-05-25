package dev.vector.proxy.protocol

enum class ProtocolState {
    HANDSHAKING,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY
}
