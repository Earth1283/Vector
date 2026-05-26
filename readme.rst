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

----

Quick start
-----------

.. code-block:: bash

   source ./setup.sh          # point JAVA_HOME at Java 21
   ./gradlew :vector-proxy:run

The proxy binds on ``*:25565``. Add ``localhost`` in your Minecraft client
and the server list entry will appear. A backend server on port ``25577``
(offline mode) is required before a player can fully connect.

----

Prerequisites
-------------

+------------+---------+--------------------------------------------------------+
| Tool       | Version | Notes                                                  |
+============+=========+========================================================+
| Java       | **21**  | Microsoft OpenJDK 21 recommended. Java 25 breaks the   |
|            |         | Kotlin DSL compiler — pin to 21.                       |
+------------+---------+--------------------------------------------------------+
| Gradle     | 8.11+   | Wrapper is bundled; ``./gradlew`` downloads it for you.|
+------------+---------+--------------------------------------------------------+
| Git        | any     |                                                        |
+------------+---------+--------------------------------------------------------+

**Codespaces / devcontainer**: ``setup.sh`` sets ``JAVA_HOME`` to the
bundled JDK at ``/usr/local/sdkman/candidates/java/21.0.10-ms``.
``gradle.properties`` already pins ``org.gradle.java.home`` to the same
path so the Gradle daemon starts automatically.

**Local**: export ``JAVA_HOME`` before running Gradle:

.. code-block:: bash

   export JAVA_HOME=/path/to/jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   java -version   # should print openjdk 21.x.x

----

Build guide
-----------

All commands run from the repository root.

Build everything
~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew build

Compiles all five modules, runs tests, and produces JARs under each
module's ``build/libs/``. ``vector-proxy`` also produces an installable
distribution under ``build/distributions/``.

Build without tests
~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew build -x test

Build a single module
~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:build

Run the proxy (development)
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:run

The proxy writes ``vector.toml`` to the working directory on first start,
then binds and logs:

.. code-block:: text

   INFO  Generating RSA key pair...
   INFO  RSA key pair ready
   INFO  Proxy bound on 0.0.0.0:25565

Run from the assembled distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:installDist
   ./vector-proxy/build/install/vector-proxy/bin/vector-proxy

This is the production path — no Gradle daemon, no recompilation on
startup.

Run tests only
~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew test

Or for a specific module:

.. code-block:: bash

   ./gradlew :vector-api:test

Build the documentation
~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   pip install -r requirements-docs.txt
   make -C docs html

Output lands at ``docs/_build/html/index.html``.

-------

Modules
-------

+------------------------+---------------------------------------------------+
| ``vector-api``         | Java-friendly plugin API. No Velocity/Netty dep.  |
+------------------------+---------------------------------------------------+
| ``vector-api-kotlin``  | Kotlin DSL (``VectorPlugin { ... }``).            |
+------------------------+---------------------------------------------------+
| ``vector-compat``      | Velocity 3.x compatibility shim.                  |
+------------------------+---------------------------------------------------+
| ``vector-proxy``       | Core: Netty pipeline, protocol, state machine.    |
+------------------------+---------------------------------------------------+
| ``hello-world-plugin`` | Example plugin (not included in proxy distribution|
|                        | — drop the built JAR into ``plugins/``).          |
+------------------------+---------------------------------------------------+

The dependency arrow points upward: ``vector-proxy`` depends on
``vector-api-kotlin`` depends on ``vector-api``. Lower modules have zero
knowledge of higher ones.

----------------

Writing a plugin
----------------

Add a dependency on ``vector-api-kotlin`` in your plugin's
``build.gradle.kts``:

.. code-block:: kotlin

   dependencies {
       compileOnly(project(":vector-api-kotlin"))
   }

Create ``src/main/resources/vector-plugin.toml``:

.. code-block:: toml

   id         = "my-plugin"
   name       = "My Plugin"
   version    = "1.0.0"
   entrypoint = "com.example.MyPlugin"
   language   = "KOTLIN"

Write the plugin class:

.. code-block:: kotlin

   class MyPlugin : VectorPlugin({
       onEnable {
           logger.info("Hello from {}!", server.version)
       }
       on<PlayerJoinEvent> { event ->
           logger.info("{} joined", event.player.username)
       }
   })

Build the JAR, drop it into ``plugins/``, and start the proxy.

See ``docs/plugin-api/kotlin-dsl.md`` for the full API reference.

----------

Three laws
----------

1. Free forever.
2. Open always.
3. We do not break userspace.

Everything else is negotiable. These are not.

-------

License
-------

`AGPL-3.0 <LICENSE>`_. Community contributions stay community property.
