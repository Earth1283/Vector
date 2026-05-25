Netty / Coroutine Boundary
==========================

Vector uses a **hybrid** model: Netty event-loop threads own the I/O layer and
coroutines handle everything above it.

Responsibilities by layer
--------------------------

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Netty event-loop thread
     - Coroutine dispatcher
   * - VarInt frame decoding
     - Plugin event handlers
   * - Cipher (future)
     - Awaitable events (login, auth)
   * - Compression (future)
     - Scheduled plugin tasks
   * - Packet decode / encode
     - Database I/O
   * - State-machine transitions
     - Cluster gossip processing

**Netty threads never block.** Every operation that touches a Netty
``ChannelHandlerContext`` or ``ByteBuf`` runs on the event-loop thread without
suspending.

Per-connection actor
--------------------

Each ``PlayerConnection`` owns a dedicated ``Channel<Packet>`` (Kotlin
coroutines channel) drained by a single coroutine. Netty enqueues decoded
packets into this channel — a non-blocking offer — and the coroutine processes
them sequentially. This guarantees per-connection packet ordering without any
locks.

Plugin event dispatch
---------------------

Plugin event handlers execute on a shared, bounded coroutine dispatcher backed
by a configurable thread pool. A handler that is slow or blocks does not stall
the Netty event loop or other connections.

Awaitable events
----------------

Some state transitions require an async result before proceeding (e.g.
``PlayerLoginEvent`` must resolve before the login sequence continues). The
connection coroutine simply ``suspend``\s waiting for the result — it is
already off the Netty thread so this is safe.

Configuration
-------------

Thread counts are exposed in the proxy TOML config:

.. code-block:: toml

   [threading]
   plugin-dispatcher-threads = 0   # 0 = auto (availableProcessors * 2)
   netty-boss-threads        = 1
   netty-worker-threads      = 0   # 0 = auto
