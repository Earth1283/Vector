Netty / Coroutine Boundary
==========================

Vector uses a **hybrid threading model**: Netty event-loop threads own the
entire I/O and codec layer, while Kotlin coroutines on ``Dispatchers.Default``
handle everything above it. Understanding this boundary is essential before
touching any network-facing code.

The Two Worlds
--------------

.. mermaid::

   flowchart TD
       subgraph Netty["Netty event-loop threads (never block)"]
           TCP[TCP read/write]
           Flush["Flush consolidation\nFlushConsolidationHandler"]
           Frame["VarInt frame decoding\nMinecraftVarintFrameDecoder"]
           Cipher["AES cipher\nMinecraftCipherDecoder/Encoder"]
           Compress["zlib compress/decompress\nMinecraftCompressDecoder/Encoder"]
           Codec["Packet decode/encode\nMinecraftPacketDecoder/Encoder"]
           Handler["Session handler dispatch\nMinecraftConnection.channelRead0"]
           StateTransition["State transitions\nsetSessionHandler()\nenableCompression()"]
       end

       subgraph Coroutines["Coroutine dispatcher (Dispatchers.Default)"]
           EventFire["Event bus fire()\nproxyScope.launch { eventBus.fire(...) }"]
           PluginHandlers["Plugin event handlers\non&lt;PlayerJoinEvent&gt; { ... }"]
           AuthHTTP["Mojang session auth\nMojangAuth.verify() — HTTP call"]
           PluginLoad["Plugin loading\nPluginManager.loadPlugins()"]
           DBWork["Database I/O\nSQLite via StorageBackend"]
           ScheduledWork["Scheduled plugin tasks\nlaunch { delay(...) }"]
       end

       Handler -->|"proxyScope.launch { }"| EventFire
       Handler -->|"CoroutineScope.launch { }"| AuthHTTP
       PluginHandlers --> DBWork
       PluginHandlers --> ScheduledWork

**The golden rule:** Code that calls any Netty ``Channel``, ``ChannelHandlerContext``,
or ``ByteBuf`` API must run on the channel's event-loop thread. Code that
suspends, blocks, or does slow I/O must not run on a Netty thread.

Responsibility Table
--------------------

.. list-table::
   :header-rows: 1
   :widths: 40 30 30

   * - Work type
     - Thread
     - Reason
   * - VarInt frame decoding
     - Netty event-loop
     - Inline in pipeline, zero allocations
   * - AES cipher (future)
     - Netty event-loop
     - Must complete before frame decode
   * - zlib compress/decompress
     - Netty event-loop
     - Inline in pipeline, before codec
   * - Packet decode/encode
     - Netty event-loop
     - Must use the channel's ``ByteBuf`` allocator
   * - State machine transitions
     - Netty event-loop
     - Atomic with the triggering packet
   * - Mojang session auth
     - Coroutine (IO/Default)
     - HTTP — blocks; must not stall other connections
   * - Plugin event handlers
     - Coroutine (Default)
     - Arbitrary plugin code — may be slow
   * - Database reads/writes
     - Coroutine (IO)
     - Blocking I/O
   * - Scheduled tasks (``delay``)
     - Coroutine (Default)
     - Timer-based, not I/O
   * - Plugin load/enable
     - Coroutine (Default)
     - Parallel classloading; CPU-bound

Crossing from Netty into Coroutines
------------------------------------

The correct pattern for dispatching from a session handler (Netty thread) to the
plugin dispatcher is ``proxyScope.launch { }``:

.. code-block:: kotlin

   // Inside a SessionHandler callback — running on Netty event-loop
   override fun handle(packet: LoginSuccessPacket): Boolean {
       swapToForwarding()   // state transition — stays on Netty thread
       // Fire event — crosses into coroutine dispatcher
       player.server.proxyScope.launch {
           player.server.eventBus.fire(PlayerJoinEvent(player))
       }
       return true
   }

``proxyScope.launch { }`` is non-blocking — it enqueues a coroutine and returns
immediately. The Netty thread is never parked.

Crossing from Coroutines back into Netty
-----------------------------------------

If a coroutine needs to write a packet or modify pipeline state, it must post
back to the channel's event loop:

.. code-block:: kotlin

   // Inside a plugin event handler — running on coroutine dispatcher
   on<PlayerJoinEvent> { event ->
       val message = fetchWelcomeMessage(event.player.uuid)  // suspending DB call
       // Post packet write back to the channel's event-loop thread
       event.player.connection.channel.eventLoop().execute {
           event.player.connection.write(ChatPacket(message))
       }
   }

Writing directly to the channel from a non-event-loop thread is **not** safe.
Netty's ``Channel.writeAndFlush()`` is thread-safe (it posts to the event loop
internally), but manipulating pipeline handlers or reading ``ByteBuf`` state is not.

Auth: The One Allowed Suspend on Netty's Edge
----------------------------------------------

The login flow requires a Mojang session server HTTP call before the player can
proceed. This is handled by ``AuthSessionHandler``, which:

1. Stops auto-reading on the channel (pauses inbound TCP).
2. Launches a coroutine on ``Dispatchers.IO`` for the HTTP call.
3. Posts the result back to the event-loop thread via ``channel.eventLoop().execute { }``.

.. mermaid::

   sequenceDiagram
       participant NE as Netty event-loop
       participant CS as Coroutine (IO)
       participant Mojang as api.mojang.com

       NE->>NE: LoginStart received
       NE->>NE: setAutoReading(false)
       NE->>CS: scope.launch(Dispatchers.IO) { verify() }
       CS->>Mojang: GET /session/minecraft/hasJoined
       Mojang-->>CS: GameProfile JSON
       CS->>NE: channel.eventLoop().execute { ... }
       NE->>NE: setAutoReading(true)
       NE->>NE: proceed with login

Flush Consolidation
-------------------

During the play (forwarding) phase, the proxy pipelines packets from one channel
directly to the other. A naïve implementation calls ``writeAndFlush()`` for each
packet, which triggers one TCP send syscall per packet — extremely wasteful when
a server sends dozens of packets per game tick.

Vector avoids this with two cooperating mechanisms:

1. **``FlushConsolidationHandler``** sits at the head of both the client and
   backend Netty pipelines (inserted before all other handlers). It intercepts
   ``flush()`` signals and batches them: instead of flushing on every write, it
   defers the actual flush to the end of the current read batch
   (``channelReadComplete``). If no read is in progress, it schedules the flush
   for the next event-loop tick via ``consolidateWhenNoReadInProgress=true``.

2. **``write`` instead of ``writeAndFlush``** in the forwarding handlers
   (``ClientPlaySessionHandler``, ``BackendPlaySessionHandler``). The handler enqueues
   the ``ByteBuf`` with ``channel.write(buf, voidPromise())`` and lets
   ``FlushConsolidationHandler`` decide when to flush.

The result: all packets arriving during a single game tick's read loop are
written to the peer channel and then flushed in one batch — typically one or
two TCP segments — regardless of how many individual packets the tick contained.

.. code-block:: kotlin

   // Forwarding handler — write, do NOT flush here
   override fun handleUnknown(buf: ByteBuf) {
       val backend = player.currentBackendConn ?: return
       if (backend.channel.isActive) {
           buf.retain()
           backend.channel.write(buf, backend.channel.voidPromise())
           // FlushConsolidationHandler will flush after channelReadComplete
       }
   }

.. note::

   Structured packets sent outside the forwarding loop — login packets,
   compression negotiation, disconnect messages — still use
   ``MinecraftConnection.write()`` which calls ``writeAndFlush()``. For those
   low-frequency control messages the per-call flush overhead is negligible
   and the ``FlushConsolidationHandler`` will still coalesce them if they
   happen to be sent within the same read cycle.

Rules Summary
-------------

.. list-table::
   :header-rows: 1
   :widths: 50 50

   * - Safe
     - Unsafe
   * - ``proxyScope.launch { }`` from a session handler
     - ``Thread.sleep()`` on a Netty thread
   * - ``channel.write()`` + ``FlushConsolidationHandler`` in forwarding path
     - Calling ``pipeline().addLast()`` from a coroutine
   * - ``channel.writeAndFlush()`` for control/login packets
     - Reading a ``ByteBuf`` after its reference count drops to zero
   * - ``channel.eventLoop().execute { }`` from a coroutine
     - Blocking network call inside a session handler callback
   * - ``delay()`` inside a coroutine
     - Sharing a non-thread-safe ``ByteBuf`` across threads
   * - ``withContext(Dispatchers.IO) { }`` for blocking work
     - Calling ``channel.write()`` without ``FlushConsolidationHandler`` in the pipeline

Configuration
-------------

Thread pool sizes are exposed in ``vector.toml``:

.. code-block:: toml

   [threading]
   plugin-dispatcher-threads = 0   # 0 = auto (availableProcessors × 2)
   netty-boss-threads        = 1   # one is sufficient for connection accept
   netty-worker-threads      = 0   # 0 = auto

In practice, ``Dispatchers.Default`` uses ``availableProcessors`` threads (same as
Kotlin's default coroutine dispatcher). For I/O-bound plugin work, use
``withContext(Dispatchers.IO)`` which uses an elastic thread pool.
