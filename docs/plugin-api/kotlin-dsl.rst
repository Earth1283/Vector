Kotlin DSL
==========

.. note:: Not yet implemented (Part 6).

Native Kotlin plugins extend ``VectorPlugin`` and configure themselves
using a builder DSL:

.. code-block:: kotlin

   class MyPlugin : VectorPlugin({

       onEnable {
           logger.info("Plugin enabled!")
       }

       onDisable {
           logger.info("Plugin disabled!")
       }

       on<PlayerJoinEvent> { event ->
           // Runs in a coroutine scope automatically.
           val data = database.getProfile(event.player.uuid)
           event.player.sendMessage(text("Welcome, ${data.displayName}!"))
       }

       command("hello") {
           permission("myplugin.hello")
           execute { sender, _ ->
               sender.sendMessage(text("Hello!"))
           }
       }

       every(5.minutes) {
           broadcast(text("Remember to vote!"))
       }
   })

Every event handler block is automatically launched in a coroutine. The
plugin receives a ``SupervisorJob``-backed scope so a handler crash does
not take down the proxy. Cancelling the scope on disable cancels all
coroutines with no thread leaks.

Plugin manifest (``vector-plugin.toml``):

.. code-block:: toml

   [plugin]
   id         = "myplugin"
   name       = "My Plugin"
   version    = "1.0.0"
   api        = "1.0"
   entrypoint = "com.example.MyPlugin"

   [vector]
   native   = true
   language = "kotlin"
