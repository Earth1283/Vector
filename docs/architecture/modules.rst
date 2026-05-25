Module Structure
================

Vector is split into four Gradle modules. The dependency graph is strict and
one-directional — nothing in the lower layers knows about the layers above it.

.. code-block:: text

   vector-api
       ↑
   vector-api-kotlin
       ↑
   vector-compat  ←──  velocity-api (compileOnly)
       ↑
   vector-proxy

``vector-api``
--------------

The public plugin API surface. Everything a plugin author compiles against lives
here. Key properties:

- **No Velocity dependency.** Clean API with no legacy baggage.
- **Java-friendly.** No Kotlin-specific constructs at the API boundary —
  Java plugins can implement every interface without a Kotlin compiler.
- Exports ``adventure-api``, ``slf4j-api``, and ``kotlinx-coroutines-core``
  as ``api`` dependencies so plugin authors get them transitively.

``vector-api-kotlin``
---------------------

The Kotlin DSL layer on top of ``vector-api``. Native Kotlin plugins depend on
this module to get the ``VectorPlugin { ... }`` builder, coroutine-native event
handlers, and other Kotlin sugar.

Keeping this separate means:

- Java plugins never pull in the Kotlin DSL or its symbols.
- The core API stays expressible in plain Java interfaces.

``vector-compat``
-----------------

The Velocity compatibility shim. It depends on ``velocity-api`` as
``compileOnly`` and implements Velocity's interfaces by delegating to
``vector-api`` equivalents.

At runtime the compat classloader makes ``velocity-api`` available to legacy
plugins. ``vector-api`` itself has zero knowledge of Velocity types.

.. note::

   If a PR causes any Velocity plugin to break, **the PR does not merge.**
   Compatibility is a hard constraint, not a goal.

``vector-proxy``
----------------

The core implementation. Contains the Netty pipeline, protocol codec, player
state machine, cluster gossip, tiered storage, console, and plugin loader.
Depends on all three modules above.

A ``vector-protocol`` sub-module is a clean future extraction point if the
packet and codec layer outgrows this module. It is not created upfront.
