package dev.vector.proxy.protocol.util

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.DecoderException
import java.util.UUID

fun ByteBuf.readVarInt(): Int {
    var result = 0
    var shift = 0
    while (true) {
        val b = readByte().toInt()
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
        if (shift >= 35) throw CorruptedFrameException("VarInt too big")
    }
}

fun ByteBuf.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if (v and 0x7F.inv() == 0) {
            writeByte(v)
            return
        }
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
}

fun ByteBuf.readString(maxLength: Int = Short.MAX_VALUE.toInt()): String {
    val byteLength = readVarInt()
    if (byteLength > maxLength * 4) throw DecoderException("String too long: $byteLength bytes")
    val bytes = ByteArray(byteLength)
    readBytes(bytes)
    val str = String(bytes, Charsets.UTF_8)
    if (str.length > maxLength) throw DecoderException("String too long: ${str.length} chars")
    return str
}

fun ByteBuf.writeString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    writeBytes(bytes)
}

fun ByteBuf.readUUID(): UUID = UUID(readLong(), readLong())

fun ByteBuf.writeUUID(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

fun ByteBuf.readByteArray(maxLength: Int = 1048576): ByteArray {
    val length = readVarInt()
    if (length > maxLength) throw DecoderException("ByteArray too long: $length > $maxLength")
    val bytes = ByteArray(length)
    readBytes(bytes)
    return bytes
}

fun ByteBuf.writeByteArray(bytes: ByteArray) {
    writeVarInt(bytes.size)
    writeBytes(bytes)
}
