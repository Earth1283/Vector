Getting Started
===============

Prerequisites
-------------

- **Java 21** (Microsoft OpenJDK 21 recommended)
- **Gradle 8.11+** (wrapper included, no manual install needed)

In the GitHub Codespace the Java 21 installation lives at
``/home/codespace/java/21.0.10-ms``. Source ``setup.sh`` at the repo root to
point ``JAVA_HOME`` and ``PATH`` at it before running any Gradle commands:

.. code-block:: bash

   source ./setup.sh

Building
--------

.. code-block:: bash

   ./gradlew build

This compiles all four modules and produces JARs under each module's
``build/libs/``. The ``vector-proxy`` module also produces a distribution
archive (``build/distributions/``) with launch scripts.

Running
-------

.. code-block:: bash

   ./gradlew :vector-proxy:run

Or from the assembled distribution:

.. code-block:: bash

   ./vector-proxy/build/install/vector-proxy/bin/vector-proxy

The proxy binds on ``*:25565`` by default. You should see:

.. code-block:: text

   INFO  Proxy bound on *:25565

At this stage (Part 2) the proxy handles the Minecraft server-list ping. Open
your Minecraft client, add ``localhost`` as a server, and the server list entry
will appear with the placeholder MOTD.

Module Overview
---------------

+--------------------+---------------------------------------------------+
| Module             | Purpose                                           |
+====================+===================================================+
| ``vector-api``     | Java-friendly plugin API surface. No Velocity     |
|                    | dependency. All plugins compile against this.     |
+--------------------+---------------------------------------------------+
| ``vector-api-      | Kotlin DSL extensions (``VectorPlugin { ... }``). |
| kotlin``           | Native Kotlin plugins add this dependency.        |
+--------------------+---------------------------------------------------+
| ``vector-compat``  | Velocity compatibility shim. Implements           |
|                    | ``velocity-api`` by delegating to ``vector-api``. |
+--------------------+---------------------------------------------------+
| ``vector-proxy``   | Core implementation: Netty pipeline, protocol,    |
|                    | state machine, cluster, storage.                  |
+--------------------+---------------------------------------------------+

Build Order (roadmap)
---------------------

.. list-table::
   :header-rows: 1
   :widths: 10 90

   * - Part
     - Milestone
   * - 1
     - Netty pipeline boots, accepts TCP ✓
   * - 2
     - Handshake + Status — appears in server list ✓
   * - 3
     - Login flow — player connects, gets kicked cleanly ✓
   * - 4
     - Encryption + Mojang auth (online mode)
   * - 5
     - Basic backend forwarding — player reaches a server
   * - 6
     - Plugin loader boots, loads hello-world plugin
   * - 7
     - Everything else: console, cluster, storage, MOTD
