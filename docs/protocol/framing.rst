Framing
=======

Every Minecraft packet is prefixed with a VarInt length field. The Netty
pipeline splits the raw TCP byte stream into discrete packet frames before any
packet-level decoding occurs.

Wire Format
-----------

Without Compression
~~~~~~~~~~~~~~~~~~~

.. code-block:: text

   +----------------------------------------------------------------------+
   |  Length (VarInt)  |  Packet ID (VarInt)  |  Payload (Length-1 bytes) |
   +----------------------------------------------------------------------+
        ^
        Byte count of (Packet ID + Payload). Does NOT include itself.

With Compression
~~~~~~~~~~~~~~~~

Enabled by ``SetCompressionPacket`` during Login:

.. code-block:: text

   +--------------------------------------------------------------------------------+
   |  Packet Length (VarInt)  |  Data Length (VarInt)  |  Data (compressed or raw)  |
   +--------------------------------------------------------------------------------+
          ^                          ^
          Byte count of              0  → packet payload is sent uncompressed
          (DataLength + Data)            (below threshold, usually 256 bytes)
                                     > 0  → Data is zlib-deflated;
                                             value is the uncompressed size

Framing Pipeline Position
~~~~~~~~~~~~~~~~~~~~~~~~~

.. mermaid::

   flowchart LR
       subgraph Inbound["Inbound (client → proxy)"]
           TCP["Raw TCP bytes"] --> FD["MinecraftVarintFrameDecoder\nstrips Length VarInt\nemits ByteBuf of exactly Length bytes"]
           FD --> CD["MinecraftCompressDecoder\n(when compression active)\nstrips DataLength VarInt\ninflates if DataLength > 0"]
           CD --> PD["MinecraftPacketDecoder\nreads Packet ID VarInt\ndispatches to packet factory"]
       end

       subgraph Outbound["Outbound (proxy → client)"]
           PE["MinecraftPacketEncoder\nwrites Packet ID + payload\nno length prefix"] --> CE["MinecraftCompressEncoder\n(replaces frame-encoder\nwhen compression active)\ndeflates if size ≥ threshold\nwrites DataLength + outer Length"]
           CE --> Wire["TCP bytes"]
           PE2["MinecraftPacketEncoder"] --> FE["MinecraftFrameEncoder\nwrites outer Length VarInt\n(when compression inactive)"]
           FE --> Wire
       end

The split between ``MinecraftPacketEncoder`` (writes bare ``[id|data]``) and
``MinecraftFrameEncoder`` / ``MinecraftCompressEncoder`` (adds the outer length) is
intentional: during play-phase forwarding the packet encoder is removed but the
framing encoder stays, so raw ``ByteBuf`` forwarding is re-framed correctly.

VarInt Encoding
---------------

VarInts use the standard base-128 variable-length encoding. Each byte carries 7
data bits; the most-significant bit signals whether more bytes follow.

.. code-block:: text

   Bit layout of each byte:
   +-------+----------------------+
   |  MSB  |    7 data bits       |
   |  0/1  |  b6 b5 b4 b3 b2 b1 b0|
   +-------+----------------------+
      ^
      1 = another byte follows
      0 = this is the last byte

Encoding Examples
~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 20 20 60

   * - Decimal
     - Hex
     - VarInt bytes
   * - 0
     - 0x00
     - ``00``
   * - 127
     - 0x7F
     - ``7F``
   * - 128
     - 0x80
     - ``80 01``
   * - 255
     - 0xFF
     - ``FF 01``
   * - 2097151
     - 0x1FFFFF
     - ``FF FF 7F``

Key limits:

- Maximum **5 bytes** for a 32-bit VarInt.
- Maximum **10 bytes** for a 64-bit VarLong.
- Packet length prefixes fit in **3 bytes** (max ~2 MB payload).

Kotlin Implementation
~~~~~~~~~~~~~~~~~~~~~

From ``ProtocolUtils.kt``:

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
--------------------------------

Extends Netty's ``ByteToMessageDecoder``. Reads the length VarInt from the
accumulation buffer; if the full payload has not yet arrived it resets the
reader index and returns, letting Netty buffer more bytes. When the complete
frame is present it emits a ``readRetainedSlice`` of exactly ``length`` bytes.

**Safety limits:**

- The length VarInt is limited to **3 bytes**. If a fourth byte is required the
  connection is closed with ``CorruptedFrameException``.
- Frames claiming to be larger than ~2 MB are rejected before any additional
  bytes are consumed.

This prevents memory exhaustion from a malicious client claiming a huge frame.

``MinecraftCompressDecoder``
-----------------------------

Active after ``SetCompressionPacket`` is received from the backend (for the
backend connection) or sent to the client (for the client connection).

.. mermaid::

   flowchart TD
       In["ByteBuf from frame-decoder\n(stripped of outer length)"]
       ReadDL["Read DataLength VarInt"]
       Zero{DataLength == 0?}
       Pass["Pass ByteBuf through\n(packet is uncompressed)"]
       Inflate["Inflate compressed bytes\nusing java.util.zip.Inflater\nexpect exactly DataLength bytes"]
       Out["Emit decompressed ByteBuf\n[PacketID | Payload]"]

       In --> ReadDL --> Zero
       Zero -->|yes| Pass --> Out
       Zero -->|no| Inflate --> Out

``MinecraftCompressEncoder``
-----------------------------

Replaces ``MinecraftFrameEncoder`` in the outbound pipeline after compression is
enabled. Handles both the compression decision and the outer length framing.

.. mermaid::

   flowchart TD
       In["ByteBuf from MinecraftPacketEncoder\n[PacketID | Payload] — no length"]
       Size{uncompressedSize\n≥ threshold?}
       Deflate["Deflate with java.util.zip.Deflater\nwrite: outer_len + dataLength + compressed"]
       Raw["Write uncompressed\nwrite: outer_len + 0x00 + raw_bytes\n(DataLength = 0 signals no compression)"]

       In --> Size
       Size -->|yes| Deflate
       Size -->|no| Raw

The ``0x00`` DataLength byte for uncompressed packets is required by the protocol
even when the payload is below threshold — it signals to the receiver that the
packet is not compressed.
