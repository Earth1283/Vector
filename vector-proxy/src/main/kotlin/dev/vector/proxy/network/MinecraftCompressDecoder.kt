package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.readVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageDecoder
import java.util.zip.Inflater

class MinecraftCompressDecoder(val threshold: Int) : MessageToMessageDecoder<ByteBuf>() {

    private val inflater = Inflater()
    // Reused per-handler output buffer; grows as needed but never shrinks below 4 KB.
    private var decompressBuffer = ByteArray(4096)

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

        // Feed compressed bytes via ByteBuffer view — avoids a heap-array copy for direct buffers.
        inflater.setInput(msg.nioBuffer())

        // Reuse the decompression scratch buffer, growing it only when needed.
        if (decompressBuffer.size < dataLength) {
            decompressBuffer = ByteArray(dataLength)
        }
        val actualLength = inflater.inflate(decompressBuffer, 0, dataLength)
        inflater.reset()

        if (actualLength != dataLength) {
            throw DecoderException("Badly compressed packet: claimed dataLength=$dataLength but inflated $actualLength bytes")
        }

        out.add(ctx.alloc().buffer(actualLength).writeBytes(decompressBuffer, 0, actualLength))
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        inflater.end()
    }

    companion object {
        private const val MAX_DECOMPRESSED_SIZE = 8 * 1024 * 1024 // 8 MB
    }
}
