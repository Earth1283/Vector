package dev.vector.proxy.network

import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import java.util.zip.Deflater

class MinecraftCompressEncoder(val threshold: Int) : MessageToByteEncoder<ByteBuf>() {

    private val deflater = Deflater()
    private var encodeBuffer = ByteArray(1024)

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val uncompressedSize = msg.readableBytes()
        if (uncompressedSize < threshold) {
            out.writeVarInt(uncompressedSize + 1)   // outer length: 1 (data-length varint) + data
            out.writeByte(0)                          // data-length = 0 (not compressed)
            out.writeBytes(msg)
        } else {
            val raw = ByteArray(uncompressedSize)
            msg.readBytes(raw)
            
            deflater.setInput(raw)
            deflater.finish()
            
            val maxCompressedSize = uncompressedSize + 100
            if (encodeBuffer.size < maxCompressedSize) {
                encodeBuffer = ByteArray(maxCompressedSize)
            }
            
            val n = deflater.deflate(encodeBuffer)
            deflater.reset()

            val dataLengthBytes = varIntSize(uncompressedSize)
            out.writeVarInt(dataLengthBytes + n)    // outer length
            out.writeVarInt(uncompressedSize)        // data-length = uncompressed size
            out.writeBytes(encodeBuffer, 0, n)
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        deflater.end()
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var size = 0
        do { v = v ushr 7; size++ } while (v != 0)
        return size
    }
}
