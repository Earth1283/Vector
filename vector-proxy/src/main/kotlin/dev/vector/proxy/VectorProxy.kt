package dev.vector.proxy

import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.network.InitialHandler
import dev.vector.proxy.network.MinecraftPacketDecoder
import dev.vector.proxy.network.MinecraftPacketEncoder
import dev.vector.proxy.network.MinecraftVarintFrameDecoder
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("vector")

fun main() {
    logger.info("Generating RSA key pair...")
    val keyPair = CryptoUtils.generateKeyPair()
    logger.info("RSA key pair ready")

    val boss = NioEventLoopGroup(1)
    val worker = NioEventLoopGroup()
    try {
        val bound = ServerBootstrap()
            .group(boss, worker)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addLast("frame-decoder", MinecraftVarintFrameDecoder())
                        .addLast("packet-decoder", MinecraftPacketDecoder())
                        .addLast("packet-encoder", MinecraftPacketEncoder())
                        .addLast("handler", InitialHandler(keyPair))
                }
            })
            .bind(25565)
            .sync()

        logger.info("Proxy bound on *:25565")
        bound.channel().closeFuture().sync()
    } finally {
        boss.shutdownGracefully()
        worker.shutdownGracefully()
    }
}
