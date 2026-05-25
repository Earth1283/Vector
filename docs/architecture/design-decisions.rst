Design Decisions
================

These decisions were settled before the first line of code. They are not up for
re-litigation unless something demonstrably breaks.

Kotlin DSL split (``vector-api`` vs ``vector-api-kotlin``)
-----------------------------------------------------------

``vector-api`` exposes a Java-friendly interface set. ``vector-api-kotlin``
adds the ``VectorPlugin { ... }`` DSL and coroutine sugar on top of it.

**Why split?** Java plugins must not be forced to depend on Kotlin DSL
symbols. Keeping the modules separate means the dependency is opt-in and the
core API surface stays expressible in idiomatic Java.

Multi-version protocol registry from day one
--------------------------------------------

Minecraft has ~30 active protocol versions. The registry was designed for
multi-version from the start rather than targeting a single version and
retrofitting later.

See :doc:`../protocol/registry` for the full design.

Hybrid Netty / coroutine dispatcher
------------------------------------

See :doc:`netty-coroutine-boundary`.

``PlayerConnection`` model
--------------------------

See :doc:`player-state-machine`.

Velocity API placement
----------------------

``velocity-api`` is a ``compileOnly`` dependency of ``vector-compat`` only.
The compat classloader exposes it to legacy plugins at runtime. ``vector-api``
has zero dependency on Velocity types — it is a clean, independent API that
happens to be semantically compatible with Velocity's contracts.

What is not negotiable
----------------------

.. code-block:: text

   Vector's Three Laws:

   1. Free forever.   (AGPL-3.0, no paid builds, no premium tiers)
   2. Open always.    (architecture decisions in public, PRs welcome)
   3. We do not break userspace.

   Everything else is negotiable.
   These are not.

The "do not break userspace" rule means: if your Velocity plugin worked on
Velocity 3.x, it works on Vector. This is enforced by the VecTest suite on
every PR and the nightly apt-mc / Modrinth compat runs.
