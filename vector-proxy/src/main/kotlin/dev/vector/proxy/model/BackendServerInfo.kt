package dev.vector.proxy.model

import java.net.InetSocketAddress

data class BackendServerInfo(
    override val name: String,
    val address: InetSocketAddress,
) : dev.vector.api.BackendServer
