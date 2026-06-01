package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.readVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageDecoder
import java.util.zip.Inflater

class MinecraftCompressDecoder(val threshold: Int) : MessageToMessageDecoder<ByteBuf>() {

    private val inflater = Inflater()

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val dataLength = msg.readVarInt()
        if (dataLength == 0) {
            out.add(msg.retain())
            return
        }
        if (dataLength < threshold) {
            throw DecoderException("Badly compressed packet: dataLength=$dataLength is below threshold=$threshold")
        }
        if (dataLength > MAX_DECOMPRESSED_SIZE) {
            throw DecoderException("Badly compressed packet: dataLength=$dataLength exceeds limit=$MAX_DECOMPRESSED_SIZE")
        }
        val compressed = ByteArray(msg.readableBytes())
        msg.readBytes(compressed)

        inflater.setInput(compressed)
        val decompressed = ByteArray(dataLength)
        val actualLength = inflater.inflate(decompressed)
        inflater.reset()

        out.add(ctx.alloc().buffer(actualLength).writeBytes(decompressed, 0, actualLength))
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        inflater.end()
    }

    companion object {
        private const val MAX_DECOMPRESSED_SIZE = 8 * 1024 * 1024 // 8 MB
    }
}
