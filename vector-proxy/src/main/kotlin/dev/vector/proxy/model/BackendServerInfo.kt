package dev.vector.proxy.model

import java.net.InetSocketAddress

data class BackendServerInfo(
    override val name: String,
    override val address: InetSocketAddress,
) : dev.vector.api.BackendServer
