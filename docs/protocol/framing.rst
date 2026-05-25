Framing
=======

The Minecraft protocol prefixes every packet with a **VarInt length** field.
The Netty pipeline splits the raw TCP byte stream into discrete packet frames
before any packet-level decoding occurs.

Wire format (no compression)
------------------------------

.. code-block:: text

   ┌─────────────────────────────────────────────────────────┐
   │  Length (VarInt)  │  Packet ID (VarInt)  │  Data ...    │
   └─────────────────────────────────────────────────────────┘

   Length = byte count of (Packet ID + Data). Does not include itself.

When compression is active (toggled during the Login phase) each frame gains
an additional **Data Length** field:

.. code-block:: text

   ┌──────────────────────────────────────────────────────────────────────┐
   │  Packet Length (VarInt)  │  Data Length (VarInt)  │  Data ...        │
   └──────────────────────────────────────────────────────────────────────┘

   Data Length = 0  → packet is uncompressed (below threshold)
   Data Length > 0  → Data is zlib-deflated; value is the uncompressed size

Compression is not implemented in the current build. The codec slot is
reserved in the pipeline.

VarInt encoding
---------------

VarInts use the standard base-128 variable-length encoding. Each byte
contributes 7 bits to the value; the MSB signals whether more bytes follow.

.. code-block:: text

   Byte layout:  [ more | b6 | b5 | b4 | b3 | b2 | b1 | b0 ]
                    ↑
                    1 = another byte follows
                    0 = this is the last byte

- Maximum 5 bytes for a 32-bit integer.
- Maximum 10 bytes for a 64-bit long.
- Packet length prefixes fit in 3 bytes (max ~2 MB).

Kotlin implementation (``ProtocolUtils.kt``):

.. code-block:: kotlin

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
           if (v and 0x7F.inv() == 0) { writeByte(v); return }
           writeByte((v and 0x7F) or 0x80)
           v = v ushr 7
       }
   }

``MinecraftVarintFrameDecoder``
-------------------------------

Extends Netty's ``ByteToMessageDecoder``. Reads the length VarInt from the
accumulation buffer; if the full payload has not yet arrived it resets the
reader index and returns, letting Netty buffer more bytes. When the complete
frame is available it emits a ``readRetainedSlice`` of exactly ``length``
bytes.

The length prefix itself is limited to **3 bytes**. Frames claiming to be
larger than ~2 MB are rejected with ``CorruptedFrameException`` before any
further bytes are consumed.

Outbound framing is handled inside ``MinecraftPacketEncoder``: after
serialising the packet ID and payload into a temporary buffer it writes a
VarInt length prefix followed by the buffer contents into the outbound
``ByteBuf``. No separate length-prepender handler is needed.
