package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.readVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import java.util.zip.Inflater

class MinecraftCompressDecoder(val threshold: Int) : MessageToMessageDecoder<ByteBuf>() {

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val dataLength = msg.readVarInt()
        if (dataLength == 0) {
            out.add(msg.retain())
            return
        }
        check(dataLength >= threshold) {
            "Badly compressed packet: dataLength=$dataLength is below threshold=$threshold"
        }
        val compressed = ByteArray(msg.readableBytes())
        msg.readBytes(compressed)
        val inflater = Inflater()
        inflater.setInput(compressed)
        val decompressed = ByteArray(dataLength)
        inflater.inflate(decompressed)
        inflater.end()
        out.add(ctx.alloc().buffer(dataLength).writeBytes(decompressed))
    }
}
