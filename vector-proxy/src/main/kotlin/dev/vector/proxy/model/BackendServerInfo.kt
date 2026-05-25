package dev.vector.proxy.model

import java.net.InetSocketAddress

data class BackendServerInfo(
    val name: String,
    val address: InetSocketAddress,
)
