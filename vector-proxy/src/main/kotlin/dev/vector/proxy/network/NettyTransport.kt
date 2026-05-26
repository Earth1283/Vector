package dev.vector.proxy.network

import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.kqueue.KQueueSocketChannel
import java.util.concurrent.ThreadFactory

object NettyTransport {
    val isEpoll: Boolean = Epoll.isAvailable()
    val isKQueue: Boolean = KQueue.isAvailable()

    fun createEventLoopGroup(threads: Int = 0): EventLoopGroup {
        return when {
            isEpoll -> EpollEventLoopGroup(threads)
            isKQueue -> KQueueEventLoopGroup(threads)
            else -> NioEventLoopGroup(threads)
        }
    }

    val serverChannelClass: Class<out ServerChannel>
        get() = when {
            isEpoll -> EpollServerSocketChannel::class.java
            isKQueue -> KQueueServerSocketChannel::class.java
            else -> NioServerSocketChannel::class.java
        }

    val clientChannelClass: Class<out Channel>
        get() = when {
            isEpoll -> EpollSocketChannel::class.java
            isKQueue -> KQueueSocketChannel::class.java
            else -> NioSocketChannel::class.java
        }
}
