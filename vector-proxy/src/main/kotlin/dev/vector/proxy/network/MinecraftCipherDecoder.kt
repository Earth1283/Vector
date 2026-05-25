package dev.vector.proxy.network

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.buffer.ByteBuf
import javax.crypto.Cipher

class MinecraftCipherDecoder(private val cipher: Cipher) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is ByteBuf) { ctx.fireChannelRead(msg); return }
        val input = ByteArray(msg.readableBytes())
        msg.readBytes(input)
        msg.release()
        ctx.fireChannelRead(Unpooled.wrappedBuffer(cipher.update(input)))
    }
}
