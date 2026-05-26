package dev.vector.proxy.network

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.buffer.ByteBuf
import javax.crypto.Cipher

class MinecraftCipherDecoder(private val cipher: Cipher) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is ByteBuf) { ctx.fireChannelRead(msg); return }
        
        val size = msg.readableBytes()
        val inNio = msg.nioBuffer(msg.readerIndex(), size)
        val outBuf = ctx.alloc().buffer(size)
        val outNio = outBuf.nioBuffer(0, size)
        
        cipher.update(inNio, outNio)
        outBuf.writerIndex(size)
        
        msg.release()
        ctx.fireChannelRead(outBuf)
    }
}
