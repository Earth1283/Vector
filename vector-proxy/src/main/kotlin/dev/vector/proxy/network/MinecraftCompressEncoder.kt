package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import java.util.zip.Deflater

class MinecraftCompressEncoder(val threshold: Int) : MessageToByteEncoder<ByteBuf>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val uncompressedSize = msg.readableBytes()
        if (uncompressedSize < threshold) {
            out.writeVarInt(uncompressedSize + 1)   // outer length: 1 (data-length varint) + data
            out.writeByte(0)                          // data-length = 0 (not compressed)
            out.writeBytes(msg)
        } else {
            val raw = ByteArray(uncompressedSize)
            msg.readBytes(raw)
            val deflater = Deflater()
            deflater.setInput(raw)
            deflater.finish()
            val compressed = ByteArray(uncompressedSize + 100)
            val n = deflater.deflate(compressed)
            deflater.end()

            val dataLengthBytes = varIntSize(uncompressedSize)
            out.writeVarInt(dataLengthBytes + n)    // outer length
            out.writeVarInt(uncompressedSize)        // data-length = uncompressed size
            out.writeBytes(compressed, 0, n)
        }
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var size = 0
        do { v = v ushr 7; size++ } while (v != 0)
        return size
    }
}
