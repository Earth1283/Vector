Packet Registry
===============

The registry maps ``(ProtocolState, Direction, ProtocolVersion, PacketID)``
to a packet factory, and maps ``(ProtocolState, Direction, ProtocolVersion,
PacketClass)`` back to a packet ID.

Design goals
------------

- **Multi-version from day one.** Packet IDs shift between Minecraft versions.
  Every lookup is version-aware.
- **Nearest-lower-version fallback.** If an exact version match is absent the
  registry finds the highest registered version that is ≤ the queried version.
  A packet registered at 1.7.2 is therefore valid for every version unless a
  later registration overrides it.
- **One packet class per packet.** Version-conditional fields live inside the
  class's ``decode``/``encode`` methods; there is no per-version subclassing.

``DirectionRegistry``
---------------------

Holds the mappings for a single ``(ProtocolState, Direction)`` pair.
Internally it uses two ``TreeMap<Int, …>`` instances keyed by protocol
version integer, which gives the floor-entry lookup for free:

.. code-block:: kotlin

   class DirectionRegistry {
       private val byId    = TreeMap<Int, MutableMap<Int, () -> MinecraftPacket>>()
       private val byClass = TreeMap<Int, MutableMap<KClass<*>, Int>>()

       fun register(
           clazz: KClass<out MinecraftPacket>,
           sinceVersion: ProtocolVersion,
           id: Int,
           factory: () -> MinecraftPacket,
       ) { ... }

       fun getFactory(packetId: Int, version: ProtocolVersion) =
           byId.floorEntry(version.protocol)?.value?.get(packetId)

       fun getPacketId(clazz: KClass<*>, version: ProtocolVersion) =
           byClass.floorEntry(version.protocol)?.value?.get(clazz)
   }

Adding a new packet that changed ID in version 1.9 looks like:

.. code-block:: kotlin

   register(FooPacket::class, MINECRAFT_1_7_2, 0x01) { FooPacket() }
   register(FooPacket::class, MINECRAFT_1_9,   0x02) { FooPacket() }

A query for version 1.8 returns the 1.7.2 entry (ID ``0x01``).
A query for version 1.9 or later returns the 1.9 entry (ID ``0x02``).

``StateRegistry``
-----------------

The singleton that assembles all ``DirectionRegistry`` instances. New packets
are registered here in the appropriate ``apply`` block:

.. code-block:: kotlin

   object StateRegistry {
       private val serverboundRegistries = mapOf(
           ProtocolState.HANDSHAKING to DirectionRegistry().apply {
               register(HandshakePacket::class, MINECRAFT_1_7_2, 0x00) { HandshakePacket() }
           },
           ProtocolState.STATUS to DirectionRegistry().apply {
               register(StatusRequestPacket::class, MINECRAFT_1_7_2, 0x00) { StatusRequestPacket() }
               register(PingRequestPacket::class,   MINECRAFT_1_7_2, 0x01) { PingRequestPacket() }
           },
       )
       ...
   }

Unknown packets (factory not found for a given ID + version) are silently
discarded by the decoder. This is intentional — the proxy should not crash on
packets it does not yet handle.

``UNKNOWN`` version handling
-----------------------------

Before the Handshake packet arrives the client's protocol version is not yet
known (``ProtocolVersion.UNKNOWN``, protocol integer ``-1``). A floor-entry
lookup with ``-1`` would return nothing.

Both ``MinecraftPacketDecoder`` and ``MinecraftPacketEncoder`` fall back to
``ProtocolVersion.MINIMUM`` (1.7.2) when the version is ``UNKNOWN``. Since
Handshake and Status packet IDs are constant across all versions this has no
practical effect and the lookup succeeds.
