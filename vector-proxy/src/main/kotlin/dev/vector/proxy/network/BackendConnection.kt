package dev.vector.proxy.network

import dev.vector.proxy.model.BackendServerInfo
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.session.BackendLoginSessionHandler
import dev.vector.proxy.protocol.Direction

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.LoggerFactory

class BackendConnection(
    val player: VectorPlayer,
    val serverInfo: BackendServerInfo,
) {
    private val logger = LoggerFactory.getLogger(BackendConnection::class.java)

    lateinit var connection: MinecraftConnection
        private set

    fun connect(onFailure: () -> Unit) {
        val clientConn = player.connection
        clientConn.setAutoReading(false)

        Bootstrap()
            .group(clientConn.channel.eventLoop())
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val backendConn = MinecraftConnection(ch)
                    connection = backendConn
                    ch.pipeline()
                        .addLast("frame-decoder", MinecraftVarintFrameDecoder())
                        .addLast("packet-decoder", MinecraftPacketDecoder(Direction.CLIENTBOUND))
                        .addLast("packet-encoder", MinecraftPacketEncoder(Direction.SERVERBOUND))
                        .addLast("frame-encoder", MinecraftFrameEncoder())
                        .addLast("handler", backendConn)
                    backendConn.setSessionHandler(
                        BackendLoginSessionHandler(
                            backendConn       = backendConn,
                            backendConnection = this@BackendConnection,
                            protocolVersion   = clientConn.protocolVersion,
                        )
                    )
                }
            })
            .connect(serverInfo.address)
            .addListener(ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    logger.warn("Could not connect to {}: {}", serverInfo.name, future.cause().message)
                    clientConn.channel.eventLoop().execute(onFailure)
                }
            })
    }
}
