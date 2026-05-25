package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class MinecraftFrameEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        out.writeVarInt(msg.readableBytes())
        out.writeBytes(msg)
    }
}
