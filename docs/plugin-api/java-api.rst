Java API
========

.. note:: Not yet implemented (Part 6).

Java plugins extend ``VectorJavaPlugin``:

.. code-block:: java

   public class MyPlugin extends VectorJavaPlugin {
       @Override
       public void onEnable() {
           events()
               .on(PlayerJoinEvent.class, this::onJoin)
               .register();
       }

       private void onJoin(PlayerJoinEvent event) {
           // CompletableFuture is provided for async work.
       }
   }

Raw thread spawning is blocked at the classloader level. Use
``CompletableFuture`` for async operations; Vector wraps it in the plugin
scope for structured cancellation.
