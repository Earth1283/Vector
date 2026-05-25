Player State Machine
====================

Every connection moves through a sealed set of states. Illegal transitions are
rejected at compile time via Kotlin's exhaustive ``when``.

States
------

.. code-block:: text

   NotConnected → Handshaking → LoggingIn → Authenticating → Limbo
                                                                  ↓
                                         Disconnecting ← InServer ↔ Transferring
                                                ↓              ↑
                                         NotConnected    Configuration

.. list-table::
   :header-rows: 1
   :widths: 25 75

   * - State
     - Description
   * - ``NotConnected``
     - No active TCP connection.
   * - ``Handshaking``
     - TCP established; client intention not yet read.
   * - ``LoggingIn``
     - Login start received; identity unverified.
   * - ``Authenticating``
     - Waiting on Mojang session server response.
   * - ``Limbo``
     - Authenticated but no backend server assigned.
   * - ``Configuration``
     - MC 1.20.2+ configuration phase (resource packs, data packs).
   * - ``InServer``
     - Fully connected to a backend server.
   * - ``Transferring``
     - Mid-switch between two backend servers.
   * - ``Disconnecting``
     - Kicked or leaving; TCP not yet torn down.

``PlayerConnection`` model
--------------------------

The connection is a plain class holding a Netty ``Channel`` and an
``AtomicReference`` to the current state:

.. code-block:: kotlin

   class PlayerConnection(val channel: Channel) {
       private val _state = AtomicReference<PlayerState>(NotConnected)
       val state: PlayerState get() = _state.get()

       fun transition(next: PlayerState) {
           val current = _state.get()
           if (!legalTransition(current, next)) throw IllegalStateTransition(current, next)
           _state.set(next)
       }
   }

``AtomicReference`` provides safe reads from any thread without locks.
Transition validation is enforced by an exhaustive ``when`` expression over the
sealed class hierarchy, so the compiler catches missing or impossible cases.

The ``PlayerState`` sealed class carries all data relevant to that state
(profile, server, protocol version, etc.) as data class properties — no mutable
fields, no ambiguity about what is valid in a given state.
