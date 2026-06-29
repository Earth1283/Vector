Packet Registry
===============

The registry maps ``(ProtocolState, Direction, ProtocolVersion, PacketID)`` to a
packet factory, and maps ``(ProtocolState, Direction, ProtocolVersion, PacketClass)``
back to a packet ID. Both directions of this mapping are needed: decoding uses
ID → factory; encoding uses class → ID.

Design Goals
------------

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Goal
     - How it's met
   * - Multi-version from day one
     - Every lookup passes a ``ProtocolVersion``; the registry finds the best match
   * - Nearest-lower-version fallback
     - ``TreeMap.headMap(version, inclusive)`` gives all entries ≤ version
   * - One class per packet
     - Version-conditional fields live inside ``decode``/``encode`` — no subclassing
   * - Fast hot-path lookup
     - ``ConcurrentHashMap`` caches resolved lookups after the first call

Lookup Algorithm
----------------

.. mermaid::

   flowchart TD
       Query["getFactory(packetId=0x03, version=1.16.4 / protocol=754)"]
       Cache{In\nfactoryCache?}
       CacheHit["Return cached result\n(ConcurrentHashMap — lock-free read)"]
       Walk["Walk byId.headMap(754, inclusive)\nin ascending version order"]
       Accumulate["For each version ≤ 754:\nif map contains packetId → update result\n(later versions override earlier ones)"]
       Store["Store result in factoryCache\n(key = packetId << 32 | version.ordinal)"]
       Return["Return factory (or null if not found)"]

       Query --> Cache
       Cache -->|yes| CacheHit
       Cache -->|no| Walk --> Accumulate --> Store --> Return

The accumulated-walk approach (rather than a simple floor-entry) is required
because a packet might have its ID reassigned in a later version. Without the
walk, registering the same class at multiple versions with different IDs would
return only the highest registered version's ID, losing the lower-version entry
for intermediate versions.

**Example — LoginStart (** ``0x00`` **→** ``0x02`` **in 1.19.3):**

.. code-block:: kotlin

   register(LoginStartPacket::class, MINECRAFT_1_7_2, 0x00) { LoginStartPacket() }
   register(LoginStartPacket::class, MINECRAFT_1_19_3, 0x02) { LoginStartPacket() }

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Query version
     - Returns
   * - 1.8 (protocol 47)
     - factory at ID ``0x00``
   * - 1.19 (protocol 759)
     - factory at ID ``0x00``
   * - 1.19.3 (protocol 761)
     - factory at ID ``0x02``
   * - 1.21.4 (protocol 769)
     - factory at ID ``0x02``

``DirectionRegistry``
---------------------

Holds mappings for a single ``(ProtocolState, Direction)`` pair. Two ``TreeMap``
instances, keyed by protocol version integer, provide the floor-entry walk:

.. code-block:: kotlin

   class DirectionRegistry {
       // protocol_int → { packet_id → factory }
       private val byId    = TreeMap<Int, MutableMap<Int, () -> MinecraftPacket>>()
       // protocol_int → { packet_class → packet_id }
       private val byClass = TreeMap<Int, MutableMap<KClass<*>, Int>>()

       // Factory cache — Long key: (packetId << 32) | version.ordinal.
       // Uses an ABSENT sentinel lambda instead of nullable to avoid a double
       // containsKey + get lookup on cache-miss for unknown packets.
       private val ABSENT: () -> MinecraftPacket = { error("absent sentinel") }
       private val factoryCache = ConcurrentHashMap<Long, () -> MinecraftPacket>()

       // ID cache — per-class IntArray indexed by ProtocolVersion.ordinal.
       // Sentinel values: Int.MIN_VALUE = not yet computed, -1 = not found.
       // Eliminates Pair object allocation on every hot lookup. Concurrent writes
       // are safe because the computation is deterministic (same class + version
       // always yields the same ID).
       private val idCacheArr = ConcurrentHashMap<KClass<*>, IntArray>()

       fun register(
           clazz: KClass<out MinecraftPacket>,
           sinceVersion: ProtocolVersion,
           id: Int,
           factory: () -> MinecraftPacket,
       ) {
           byId.getOrPut(sinceVersion.protocol) { mutableMapOf() }[id] = factory
           byClass.getOrPut(sinceVersion.protocol) { mutableMapOf() }[clazz] = id
           factoryCache.clear()   // invalidate both caches on schema change
           idCacheArr.clear()
       }

       fun getFactory(packetId: Int, version: ProtocolVersion): (() -> MinecraftPacket)? {
           val key = (packetId.toLong() shl 32) or version.ordinal.toLong()
           factoryCache[key]?.let { return if (it === ABSENT) null else it }
           var result: (() -> MinecraftPacket)? = null
           byId.headMap(version.protocol, true).values.forEach { map ->
               map[packetId]?.also { result = it }
           }
           factoryCache[key] = result ?: ABSENT
           return result
       }

       fun getPacketId(clazz: KClass<*>, version: ProtocolVersion): Int? {
           val arr = idCacheArr.computeIfAbsent(clazz) {
               IntArray(ProtocolVersion.entries.size) { Int.MIN_VALUE }
           }
           val idx = version.ordinal
           val v = arr[idx]
           if (v != Int.MIN_VALUE) return if (v == -1) null else v
           var result = -1
           byClass.headMap(version.protocol, true).values.forEach { map ->
               map[clazz]?.also { result = it }
           }
           arr[idx] = result
           return if (result == -1) null else result
       }
   }

**Cache key design:**

- ``factoryCache`` encodes ``packetId`` and ``version.ordinal`` into a single
  ``Long`` — zero object allocation per lookup after warmup.
- ``idCacheArr`` stores one ``IntArray`` per packet class, indexed by
  ``ProtocolVersion.ordinal``. Compared to the previous ``Pair<KClass<*>, ProtocolVersion>``
  key, this eliminates a heap allocation on every lookup during the cache-warm
  phase and reduces cache memory from one ``Pair`` object per (class, version)
  tuple to one ``IntArray`` per class.
- Both caches use sentinel values instead of nullable entries so a single map
  ``get()`` distinguishes "cached not-found" from "not yet cached", avoiding
  a secondary ``containsKey`` call.

``StateRegistry``
-----------------

The singleton that assembles all ``DirectionRegistry`` instances. Each
``(ProtocolState, Direction)`` pair gets its own registry:

.. mermaid::

   graph TD
       SR["StateRegistry (object)"]
       SR --> HSSB["HANDSHAKING / SERVERBOUND\nHandshakePacket (0x00)"]
       SR --> STSB["STATUS / SERVERBOUND\nStatusRequestPacket (0x00)\nPingRequestPacket (0x01)"]
       SR --> STCB["STATUS / CLIENTBOUND\nStatusResponsePacket (0x00)\nPingResponsePacket (0x01)"]
       SR --> LGSB["LOGIN / SERVERBOUND\nLoginStartPacket (0x00)\nEncryptionResponsePacket (0x01)\nLoginPluginResponsePacket (0x02)\nLoginAcknowledgedPacket (0x03)"]
       SR --> LGCB["LOGIN / CLIENTBOUND\nLoginDisconnectPacket (0x00)\nEncryptionRequestPacket (0x01)\nLoginSuccessPacket (0x02)\nSetCompressionPacket (0x03)\nLoginPluginMessagePacket (0x04)"]

New packets are registered inside the appropriate ``apply`` block:

.. code-block:: kotlin

   object StateRegistry {
       val SERVERBOUND = mapOf(
           ProtocolState.LOGIN to DirectionRegistry().apply {
               register(LoginStartPacket::class, MINECRAFT_1_7_2, 0x00) { LoginStartPacket() }
               register(LoginStartPacket::class, MINECRAFT_1_19_3, 0x02) { LoginStartPacket() }
               register(EncryptionResponsePacket::class, MINECRAFT_1_7_2, 0x01) { EncryptionResponsePacket() }
               register(LoginPluginResponsePacket::class, MINECRAFT_1_7_2, 0x02) { LoginPluginResponsePacket() }
               register(LoginAcknowledgedPacket::class, MINECRAFT_1_7_2, 0x03) { LoginAcknowledgedPacket() }
           },
           // ...
       )
   }

Unknown Packets
---------------

If ``getFactory()`` returns ``null`` for a given ``(packetId, version)`` pair, the
decoder silently discards the ``ByteBuf``. This is intentional: the proxy must
not crash on packets it does not yet handle — they are simply forwarded as raw
bytes during play phase.

During login phase, unknown packets from the client are dropped. Unknown packets
from the backend are also dropped unless the connection is in play-forwarding
mode, in which case raw forwarding bypasses the registry entirely.

``UNKNOWN`` Version Bootstrap
------------------------------

Before the ``HandshakePacket`` arrives the client's protocol version is
``ProtocolVersion.UNKNOWN`` (protocol integer ``-1``). A floor-entry lookup with
``-1`` would find nothing in the ``TreeMap``.

Both ``MinecraftPacketDecoder`` and ``MinecraftPacketEncoder`` fall back to
``ProtocolVersion.MINIMUM`` (1.7.2, protocol integer ``4``) when the version is
``UNKNOWN``. Since Handshake and Status packet IDs have not changed across any
version, this fallback always succeeds.

.. mermaid::

   flowchart LR
       Check{version ==\nUNKNOWN?}
       UseMin["Use MINIMUM (1.7.2)"]
       UseReal["Use actual version"]
       Lookup["Registry lookup"]

       Check -->|yes| UseMin --> Lookup
       Check -->|no| UseReal --> Lookup
