Packets
=======

Every packet in Vector implements the ``MinecraftPacket`` interface:

.. code-block:: kotlin

   interface MinecraftPacket {
       fun decode(buf: ByteBuf, version: ProtocolVersion)
       fun encode(buf: ByteBuf, version: ProtocolVersion)
   }

``decode`` mutates the packet instance from the provided ``ByteBuf`` (which
contains exactly one packet's payload, with the packet ID already consumed).
``encode`` writes the packet's fields into the provided ``ByteBuf`` (packet ID
is written by the encoder, not the packet itself).

The ``ProtocolVersion`` parameter allows a single packet class to handle
version-conditional fields without subclassing.

Implemented packets
-------------------

Login state
~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 15 15 25 45

   * - Direction
     - ID
     - Class
     - Fields
   * - Serverbound
     - ``0x00``
     - ``LoginStartPacket``
     - ``username`` (String), ``uuid`` (UUID, 1.19.3+)
   * - Clientbound
     - ``0x00``
     - ``LoginDisconnectPacket``
     - ``reason`` (JSON text component String)

``LoginStartPacket`` is version-aware: 1.19.3+ (protocol ≥ 761) includes a
UUID field directly; earlier versions appended chat-signing fields that are
skipped with ``buf.skipBytes(buf.readableBytes())``.

``LoginDisconnectPacket`` carries the kick reason as a serialised Adventure
JSON text component, e.g. ``{"text":"...","color":"gold"}``.

Login flow (current behaviour — Part 3)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: text

   Client                              Vector
     │                                    │
     │── Handshake (0x00, nextState=2) ──▶│  HandshakePacket
     │                                    │  → transitionTo(LOGIN)
     │── Login Start (0x00, username) ───▶│  LoginStartPacket
     │                                    │  → log attempt
     │◀─ Login Disconnect (0x00) ─────────│  LoginDisconnectPacket
     │   "Vector is not yet ready"        │  (connection closed)

Encryption (Part 4) and backend forwarding (Part 5) will replace the kick
with a real login sequence.

Handshaking state
~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 15 15 15 55

   * - Direction
     - ID
     - Class
     - Fields
   * - Serverbound
     - ``0x00``
     - ``HandshakePacket``
     - ``protocolVersion``, ``serverAddress``, ``serverPort``, ``nextState``

``nextState``: ``1`` → Status, ``2`` → Login.

Status state
~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 15 15 25 45

   * - Direction
     - ID
     - Class
     - Fields
   * - Serverbound
     - ``0x00``
     - ``StatusRequestPacket``
     - *(none)*
   * - Serverbound
     - ``0x01``
     - ``PingRequestPacket``
     - ``payload`` (Long)
   * - Clientbound
     - ``0x00``
     - ``StatusResponsePacket``
     - ``json`` (String — see below)
   * - Clientbound
     - ``0x01``
     - ``PingResponsePacket``
     - ``payload`` (Long — echoed from request)

Status response JSON
~~~~~~~~~~~~~~~~~~~~

The status response ``json`` field carries the server list entry as a JSON
string. The structure is fixed by the Minecraft protocol:

.. code-block:: json

   {
     "version":     { "name": "1.21.4", "protocol": 769 },
     "players":     { "max": 100, "online": 0, "sample": [] },
     "description": { "text": "A Vector Proxy" },
     "favicon":     "data:image/png;base64,..."
   }

Vector builds this JSON in ``InitialHandler.buildStatusJson`` using the
protocol version negotiated during the Handshake. The MOTD DSL (Part 7) will
replace this with the full configurable MOTD system.

Packet pipeline flow (status ping)
------------------------------------

.. code-block:: text

   Client                              Vector
     │                                    │
     │── Handshake (0x00, nextState=1) ──▶│  HandshakePacket
     │                                    │  → transitionTo(STATUS)
     │── Status Request (0x00) ──────────▶│  StatusRequestPacket
     │                                    │  → buildStatusJson()
     │◀─ Status Response (0x00) ──────────│  StatusResponsePacket
     │── Ping Request (0x01, payload) ───▶│  PingRequestPacket
     │◀─ Ping Response (0x01, payload) ───│  PingResponsePacket
     │                                    │  (connection closed)
