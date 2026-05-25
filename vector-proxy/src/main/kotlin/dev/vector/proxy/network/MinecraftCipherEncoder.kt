package dev.vector.proxy.network

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import javax.crypto.Cipher

class MinecraftCipherEncoder(private val cipher: Cipher) : ChannelOutboundHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is ByteBuf) { ctx.write(msg, promise); return }
        val input = ByteArray(msg.readableBytes())
        msg.readBytes(input)
        msg.release()
        ctx.write(Unpooled.wrappedBuffer(cipher.update(input)), promise)
    }
}
