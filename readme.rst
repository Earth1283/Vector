Vector
======

**A Minecraft proxy. Fast. Open. Free. Always.**

A spiritual successor to Velocity built on Kotlin + Netty. Clean-room
reimplementation with a better architecture, native coroutines, a real
terminal, cluster-aware design, and an AGPL-3.0 license.

.. list-table::
   :widths: 25 75

   * - **Language**
     - Kotlin
   * - **Async I/O**
     - Netty + Kotlin coroutines
   * - **License**
     - AGPL-3.0
   * - **Plugin compat**
     - Velocity 3.x (drop-in), native Kotlin DSL, Java

Quick start
-----------

.. code-block:: bash

   source ./setup.sh          # point JAVA_HOME at Java 21
   ./gradlew :vector-proxy:run

The proxy binds on ``*:25565``. Add ``localhost`` in your Minecraft client
and the server list entry will appear.

Build
-----

.. code-block:: bash

   ./gradlew build            # compile all modules
   ./gradlew :vector-proxy:run

Requires **Java 21**. The Gradle wrapper (``./gradlew``) downloads the right
Gradle version automatically — no manual install needed.

Modules
-------

+----------------------+---------------------------------------------------+
| ``vector-api``       | Java-friendly plugin API. No Velocity dependency. |
+----------------------+---------------------------------------------------+
| ``vector-api-kotlin``| Kotlin DSL (``VectorPlugin { ... }``).            |
+----------------------+---------------------------------------------------+
| ``vector-compat``    | Velocity 3.x compatibility shim.                  |
+----------------------+---------------------------------------------------+
| ``vector-proxy``     | Core: Netty pipeline, protocol, state machine.    |
+----------------------+---------------------------------------------------+

Docs
----

.. code-block:: bash

   pip install -r requirements-docs.txt
   make -C docs html
   # output: docs/_build/html/index.html

Three laws
----------

1. Free forever.
2. Open always.
3. We do not break userspace.

Everything else is negotiable. These are not.

License
-------

`AGPL-3.0 <LICENSE>`_. Community contributions stay community property.
