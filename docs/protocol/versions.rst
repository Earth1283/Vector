Protocol Versions
=================

Vector defines every known Minecraft Java Edition protocol version as a
``ProtocolVersion`` enum entry. The ``protocol`` property is the integer sent in
the ``HandshakePacket``; ``versionString`` is the human-readable label used in
logs and the server list status response.

.. code-block:: text

   enum class ProtocolVersion(val protocol: Int, val versionString: String) {
       UNKNOWN(-1, "Unknown"),             // Before Handshake is received
       MINECRAFT_1_7_2(4, "1.7.2"),       // Oldest supported
       // ...
       MINECRAFT_1_21_4(769, "1.21.4");   // Current maximum

       companion object {
           fun fromProtocol(protocol: Int): ProtocolVersion  // returns UNKNOWN if not found
           val MINIMUM = MINECRAFT_1_7_2
           val MAXIMUM = MINECRAFT_1_21_4
       }
   }

Supported Versions
------------------

.. list-table::
   :header-rows: 1
   :widths: 35 15 20 30

   * - Enum constant
     - Protocol
     - Version string
     - Notable changes
   * - ``MINECRAFT_1_7_2``
     - 4
     - 1.7.2
     - Baseline — oldest supported
   * - ``MINECRAFT_1_7_10``
     - 5
     - 1.7.10
     -
   * - ``MINECRAFT_1_8``
     - 47
     - 1.8
     - Chat signing introduced
   * - ``MINECRAFT_1_9``
     - 107
     - 1.9
     - Off-hand slot; elytra
   * - ``MINECRAFT_1_9_4``
     - 110
     - 1.9.4
     -
   * - ``MINECRAFT_1_10``
     - 210
     - 1.10
     -
   * - ``MINECRAFT_1_11``
     - 315
     - 1.11
     -
   * - ``MINECRAFT_1_12``
     - 335
     - 1.12
     -
   * - ``MINECRAFT_1_12_2``
     - 340
     - 1.12.2
     -
   * - ``MINECRAFT_1_13``
     - 393
     - 1.13
     - Chat/command changes
   * - ``MINECRAFT_1_14``
     - 477
     - 1.14
     -
   * - ``MINECRAFT_1_15``
     - 573
     - 1.15
     -
   * - ``MINECRAFT_1_16``
     - 735
     - 1.16
     -
   * - ``MINECRAFT_1_16_4``
     - 754
     - 1.16.4/1.16.5
     -
   * - ``MINECRAFT_1_17``
     - 755
     - 1.17
     -
   * - ``MINECRAFT_1_17_1``
     - 756
     - 1.17.1
     -
   * - ``MINECRAFT_1_18``
     - 757
     - 1.18/1.18.1
     -
   * - ``MINECRAFT_1_18_2``
     - 758
     - 1.18.2
     -
   * - ``MINECRAFT_1_19``
     - 759
     - 1.19
     - Chat signing; player reports
   * - ``MINECRAFT_1_19_1``
     - 760
     - 1.19.1/1.19.2
     -
   * - ``MINECRAFT_1_19_3``
     - 761
     - 1.19.3
     - LoginStart UUID field added
   * - ``MINECRAFT_1_19_4``
     - 762
     - 1.19.4
     -
   * - ``MINECRAFT_1_20``
     - 763
     - 1.20/1.20.1
     -
   * - ``MINECRAFT_1_20_2``
     - 764
     - 1.20.2
     - **Configuration phase** added; LoginAcknowledged required
   * - ``MINECRAFT_1_20_3``
     - 765
     - 1.20.3/1.20.4
     -
   * - ``MINECRAFT_1_20_5``
     - 766
     - 1.20.5/1.20.6
     -
   * - ``MINECRAFT_1_21``
     - 767
     - 1.21/1.21.1
     -
   * - ``MINECRAFT_1_21_2``
     - 768
     - 1.21.2/1.21.3
     -
   * - ``MINECRAFT_1_21_4``
     - 769
     - 1.21.4
     - Current maximum

Version-sensitive Proxy Logic
-------------------------------

A small number of login flow decisions are gated on the protocol version:

.. mermaid::

   flowchart TD
       LSuccess["Backend sends LoginSuccess"]
       Check{protocol ≥ 764?\n(1.20.2+)}
       SendAck["Send LoginAcknowledged\nto backend"]
       SwapNow["swapToForwarding() immediately"]
       WaitAck["Wait for client's\nLoginAcknowledged"]
       SwapAfter["swapToForwarding()"]

       LSuccess --> Check
       Check -->|yes — 1.20.2+| SendAck --> WaitAck --> SwapAfter
       Check -->|no — pre-1.20.2| SwapNow

The ``Configuration`` phase introduced in 1.20.2 sits between ``Login`` and ``Play``.
The proxy currently passes through this phase transparently via raw forwarding
after ``LoginAcknowledged`` is exchanged.

A second version gate concerns ``LoginStart``:

.. mermaid::

   flowchart TD
       LS["LoginStart received"]
       Check{protocol ≥ 761?\n(1.19.3+)}
       ReadUUID["Read UUID field\nfrom LoginStart"]
       GenerateUUID["Generate UUID offline\nfrom username (md5 hash)"]

       LS --> Check
       Check -->|yes| ReadUUID
       Check -->|no| GenerateUUID

Adding a New Version
--------------------

1. Add the entry to ``ProtocolVersion.kt``:

   .. code-block:: kotlin

      MINECRAFT_X_Y_Z(protocol_int, "X.Y.Z"),

2. Update ``MAXIMUM`` in the companion object if this is the newest version.
3. In ``StateRegistry``, register any packet IDs that changed in this version:

   .. code-block:: kotlin

      register(SomePacket::class, MINECRAFT_X_Y_Z, 0xNN) { SomePacket() }

4. If a packet's field layout changed, add a version branch in the packet's
   ``decode``/``encode``:

   .. code-block:: kotlin

      override fun decode(buf: ByteBuf, version: ProtocolVersion) {
          username = buf.readString()
          if (version.protocol >= ProtocolVersion.MINECRAFT_X_Y_Z.protocol) {
              newField = buf.readString()
          }
      }

5. Update this page's version table.
6. Run the VecTest suite to confirm no regressions.

Version Negotiation
-------------------

The proxy does not enforce a minimum or maximum version. Any version in the
``ProtocolVersion`` enum that is sent in the client's ``HandshakePacket`` is
accepted. If the client sends an unrecognised protocol integer, ``fromProtocol``
returns ``UNKNOWN`` and the connection proceeds but packet decoding falls back to
``MINIMUM`` (1.7.2) lookups.

Enforcing a minimum client version (if desired) is a plugin concern:

.. code-block:: kotlin

   on<PlayerJoinEvent> { event ->
       val player = event.player as dev.vector.proxy.model.VectorPlayer
       if (player.protocolVersion.protocol < ProtocolVersion.MINECRAFT_1_20.protocol) {
           player.disconnect("Minimum supported version is 1.20")
       }
   }
