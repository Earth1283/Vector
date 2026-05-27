package dev.vector.api

import java.net.InetSocketAddress

interface BackendServer {
    val name: String
    val address: InetSocketAddress
}
