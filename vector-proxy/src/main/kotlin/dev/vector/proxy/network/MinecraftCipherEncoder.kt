package dev.vector.proxy.network

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import javax.crypto.Cipher

class MinecraftCipherEncoder(private val cipher: Cipher) : ChannelOutboundHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is ByteBuf) { ctx.write(msg, promise); return }
        
        val size = msg.readableBytes()
        val inNio = msg.nioBuffer(msg.readerIndex(), size)
        val outBuf = ctx.alloc().buffer(size)
        val outNio = outBuf.nioBuffer(0, size)
        
        cipher.update(inNio, outNio)
        outBuf.writerIndex(size)
        
        msg.release()
        ctx.write(outBuf, promise)
    }
}
