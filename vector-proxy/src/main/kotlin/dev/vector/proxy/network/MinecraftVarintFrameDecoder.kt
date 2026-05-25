package dev.vector.proxy.network

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException

class MinecraftVarintFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (!buf.isReadable) return

        buf.markReaderIndex()

        var length = 0
        var shift = 0
        var bytesRead = 0

        while (true) {
            if (!buf.isReadable) {
                buf.resetReaderIndex()
                return
            }
            val b = buf.readByte().toInt()
            bytesRead++
            length = length or ((b and 0x7F) shl shift)
            shift += 7
            if (b and 0x80 == 0) break
            // Packet length fits in 3 bytes (max ~2MB). Anything larger is malformed.
            if (bytesRead >= 3) throw CorruptedFrameException("Length VarInt too large")
        }

        if (buf.readableBytes() < length) {
            buf.resetReaderIndex()
            return
        }

        out.add(buf.readRetainedSlice(length))
    }
}
