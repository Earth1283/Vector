Design Decisions
================

These decisions were settled before the first line of code. They are not up for
re-litigation unless something demonstrably breaks in production. Each entry
records the decision, the reasoning, and the trade-offs accepted.

API Split: ``vector-api`` vs ``vector-api-kotlin``
--------------------------------------------------

**Decision:** The public plugin API (``vector-api``) is a plain Java-compatible
module. Kotlin DSL sugar lives in a separate ``vector-api-kotlin`` module that
plugin authors opt into.

**Reasoning:**

.. mermaid::

   flowchart LR
       subgraph Without split
           KotlinAPI["vector-api\n(Kotlin DSL baked in)"]
           JavaPlugin["Java plugin\nmust compile against\nKotlin stdlib + DSL"]
           KotlinPlugin2["Kotlin plugin\nworks fine"]
           KotlinAPI --> JavaPlugin
           KotlinAPI --> KotlinPlugin2
       end

       subgraph With split
           PlainAPI["vector-api\n(Java-friendly interfaces)"]
           KotlinDSL["vector-api-kotlin\n(DSL sugar, optional)"]
           JavaPlugin2["Java plugin\ncompiles against\nvector-api only"]
           KotlinPlugin3["Kotlin plugin\ncompiles against\nboth modules"]
           PlainAPI --> JavaPlugin2
           PlainAPI --> KotlinDSL
           KotlinDSL --> KotlinPlugin3
           PlainAPI --> KotlinPlugin3
       end

**Trade-offs accepted:** Plugin authors need to know to add ``vector-api-kotlin``
as a dependency. The module list is one step more complex. Both are acceptable
costs for a clean API.

Multi-version Protocol Registry from Day One
--------------------------------------------

**Decision:** The packet registry is version-aware from the start, using a
``TreeMap<Int, …>`` keyed by protocol version for floor-entry lookups.

**Reasoning:** Minecraft has ~30 active protocol versions simultaneously.
Retrofitting a version-aware registry onto a single-version design after the
fact typically requires either a complete rewrite or accumulated hacks. Starting
version-aware costs roughly the same as a single-version registry but buys
permanent multi-version support.

.. mermaid::

   flowchart LR
       Q["getFactory(id=0x03, version=1.16)"]
       T1["1.7.2 → {0x01: LoginStart, 0x02: ...}"]
       T2["1.8   → {0x01: LoginStart, ...}"]
       T3["1.19  → {0x02: LoginStart, ...}"]
       T4["1.20.2→ {0x02: LoginStart, 0x03: LoginAck}"]

       Q -->|"headMap(1.16, inclusive)\nwalk in order"| T1
       Q --> T2
       Q -->|"highest ≤ 1.16 for 0x03"| T3
       Q -. "not reached, 1.20.2 > 1.16" .-> T4

The ``headMap(version, inclusive)`` walk accumulates entries from oldest to
newest — later registrations override earlier ones for the same ID. A packet
registered at 1.7.2 is therefore valid for every version unless overridden.

**Trade-offs accepted:** Slightly more complex ``DirectionRegistry`` than a plain
``HashMap<Int, Factory>``. The ``TreeMap`` floor-entry overhead is negligible
compared to packet decode/encode and is mitigated by the ``ConcurrentHashMap``
lookup cache added in Part 6.

Hybrid Netty / Coroutine Dispatcher
-------------------------------------

**Decision:** Netty event-loop threads own all I/O and codec work. Coroutines on
``Dispatchers.Default`` handle all plugin events, async state transitions, and
blocking work. The two never mix on the same thread.

See :doc:`netty-coroutine-boundary` for the full treatment.

**Reasoning:** Blocking a Netty event-loop thread stalls every connection
sharing that thread. Plugin code — which can be arbitrarily slow — must not run
on event-loop threads.

**Trade-offs accepted:** Every plugin event dispatch goes through
``proxyScope.launch { }``, adding one context switch. For the expected event
rates on a Minecraft proxy this is negligible.

Velocity API as ``compileOnly`` in ``vector-compat``
----------------------------------------------------

**Decision:** ``velocity-api`` is a ``compileOnly`` dependency of ``vector-compat``.
It is not bundled in the proxy distribution. The compat classloader exposes it
to legacy plugins at runtime.

**Reasoning:** Bundling ``velocity-api`` in the proxy JAR would require
distributing it under its own license and would make every proxy process carry
~2 MB of Velocity API even if no Velocity plugins are loaded. The ``compileOnly``
approach means ``vector-api`` itself has zero Velocity contamination.

**Trade-offs accepted:** The compat classloader is slightly more complex (it
must locate ``velocity-api`` at runtime). Acceptable given the benefits.

``PlayerConnection`` Uses ``SessionHandler`` Pattern, Not State Enum
--------------------------------------------------------------------

**Decision:** Connection state is represented as a swappable ``SessionHandler``
implementation (Strategy pattern) rather than a state enum + ``when`` dispatch.

**Reasoning:** Each connection phase (Handshake, Login, Play, etc.) requires a
completely different set of packet handlers. An enum + ``when`` grows linearly
with packet count. A ``SessionHandler`` interface with per-phase implementations
keeps each phase's logic isolated and independently testable.

.. mermaid::

   classDiagram
       class MinecraftConnection {
           +channel: Channel
           +setSessionHandler(SessionHandler)
           +channelRead0(ByteBuf)
       }
       class SessionHandler {
           <<interface>>
           +connected()
           +activated()
           +handle(packet) Boolean
           +handleUnknown(ByteBuf)
           +disconnected()
           +exception(Throwable)
       }
       MinecraftConnection --> SessionHandler : delegates to

       SessionHandler <|.. HandshakeSessionHandler
       SessionHandler <|.. LoginSessionHandler
       SessionHandler <|.. AuthSessionHandler
       SessionHandler <|.. ClientLoginSuccessSessionHandler
       SessionHandler <|.. BackendLoginSessionHandler
       SessionHandler <|.. ClientPlaySessionHandler
       SessionHandler <|.. BackendPlaySessionHandler

**Trade-offs accepted:** More files (one per phase). Cross-phase data must be
passed explicitly (e.g. ``VectorPlayer`` is passed to play handlers). Both are
net positives for readability.

``swapToForwarding()`` Keeps Frame/Compress Codecs
--------------------------------------------------

**Decision:** When a connection enters play phase (``swapToForwarding()``),
only ``packet-decoder`` and ``packet-encoder`` are removed from the Netty pipeline.
``frame-decoder``, ``compress-decoder``, and ``compress-encoder`` remain.

**Reasoning:** Play packets are forwarded as opaque ``ByteBuf``s. If compression
is active, the raw bytes that arrive at ``handleUnknown()`` must already be
decompressed — so ``compress-decoder`` must stay in the pipeline. When those
bytes are written to the peer channel, the peer's ``compress-encoder`` and
``frame-encoder`` re-compress and re-frame them. Neither side needs to know
anything about the other's compression state.

.. mermaid::

   flowchart LR
       subgraph Client pipeline after swapToForwarding
           FD["frame-decoder\n✓ stays"] --> CD["compress-decoder\n✓ stays"]
           CD --> PD["packet-decoder\n✗ removed"]
           PD2["packet-encoder\n✗ removed"] --> CE["compress-encoder\n✓ stays"]
       end
       subgraph Backend pipeline after swapToForwarding
           FD2["frame-decoder\n✓ stays"] --> CD2["compress-decoder\n✓ stays"]
           CD2 --> PD3["packet-decoder\n✗ removed"]
           PD4["packet-encoder\n✗ removed"] --> CE2["compress-encoder\n✓ stays"]
       end

**Trade-offs accepted:** ``handleUnknown(buf)`` receives decompressed ``[id|data]``
bytes. Any play packet the proxy does inspect (future: tab list, keep-alive)
must be re-parsed from this raw form. Acceptable — proxies rarely need to
inspect most play packets.

Three Laws (Non-negotiable)
---------------------------

These are not design decisions subject to trade-off analysis. They are axioms.

1. **Free forever.** AGPL-3.0. No paid builds, no premium tiers, no "enterprise
   edition". The licence is chosen precisely to close the "run modified software
   as a service without releasing source" loophole.

2. **Open always.** Architecture decisions made in public. PRs welcome. No
   private roadmap forks.

3. **We do not break userspace.** If your Velocity plugin worked on Velocity 3.x,
   it works on Vector. If a PR breaks this, the PR does not merge. No exceptions.
