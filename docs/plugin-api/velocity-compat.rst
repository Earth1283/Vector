Velocity Compatibility
======================

.. note:: Not yet implemented (Part 6).

Existing Velocity plugins run unmodified on Vector. Place a Velocity plugin
JAR in the ``plugins/`` directory; if its ``velocity-plugin.json`` manifest
is present Vector loads it through the compatibility layer.

The compat layer wraps Vector's internals behind Velocity's API interfaces:

.. code-block:: kotlin

   class VelocityCompatLayer(private val plugin: Any) {
       fun getServer(): ProxyServer         = VelocityProxyServerShim(vector)
       fun getLogger(): Logger              = VelocityLoggerShim(plugin.id)
       fun getEventManager(): EventManager  = VelocityEventManagerShim(vector)
       fun getCommandManager(): CommandManager = VelocityCommandManagerShim(vector)
       fun getScheduler(): Scheduler        = VelocitySchedulerShim(vector)
   }

Load-time console output distinguishes compat plugins from native ones:

.. code-block:: text

   INFO  PLUG  EssentialsX  v2.20.1  legacy velocity  compat mode
   INFO  PLUG  MyPlugin     v1.0.0   vector native    api 1.0
   INFO  PLUG  WeirdPlugin  v0.1.0   rejected         no manifest

Compatibility guarantee
-----------------------

If your plugin worked on Velocity 3.x it must work on Vector. This is
enforced by two mechanisms:

- **VecTest** runs on every PR and calls every Velocity and Vector API
  endpoint. A single failure blocks the merge.
- **apt-mc** runs nightly, downloads the top-20 Velocity plugins by
  follower count from Modrinth, and runs them against a live Vector
  test harness. Failures auto-create GitHub tracking issues.
