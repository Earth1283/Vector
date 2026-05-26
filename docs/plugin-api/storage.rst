Storage
=======

Vector provides a shared SQLite database that plugins can use to persist data
across restarts. Each plugin manages its own schema through Flyway SQL migrations
bundled inside the plugin JAR.

Overview
--------

The storage layer is exposed on ``ProxyServer`` as ``server.storage`` — an
implementation of ``StorageBackend``. Two interfaces form the public API:

.. code-block:: text

   TransactionScope
     query(sql, params, mapper) → List<T>   -- SELECT
     execute(sql, params) → Int             -- INSERT / UPDATE / DELETE

   StorageBackend : TransactionScope
     transaction { … }                      -- grouped operations
     migrate(pluginId, classLoader, location)
     close()

Both live in ``dev.vector.api.storage``.

SQLite Backend
--------------

The default implementation is SQLite via ``jdbc:sqlite:``. The database file is
set in ``vector.toml`` and defaults to ``data/vector.db``.

- **Journal mode**: WAL — readers never block writers; writers never block readers.
- **Foreign keys**: enabled by default.
- **Access model**: single JDBC connection; a ``Mutex`` serialises all operations.

Migrations — ``migrate()``
--------------------------

Migrations use `Flyway <https://flywaydb.org>`_ 9.x. Each plugin gets its own
Flyway history table so migration versions don't collide across plugins.

Place versioned SQL files in ``src/main/resources/db/migration/``:

.. code-block:: text

   src/main/resources/
   └── db/migration/
       ├── V1__init.sql
       └── V2__add_index.sql

File naming: ``V<version>__<description>.sql`` (two underscores). Versions must
be monotonically increasing integers.

Call ``migrate()`` from ``onEnable`` — it runs any unapplied migrations and is a
no-op if the schema is already current:

.. code-block:: kotlin

   onEnable {
       migrate()   // applies db/migration/*.sql from this plugin's JAR
       logger.info("Schema ready.")
   }

``migrate()`` is DSL sugar for:

.. code-block:: kotlin

   server.storage.migrate(pluginId, classLoader, "db/migration")

The ``pluginId`` and ``classLoader`` are supplied automatically from the scope.

Querying
--------

``query`` maps each result-set row through a lambda:

.. code-block:: kotlin

   val count = server.storage.query(
       "SELECT count(*) FROM hello_sessions"
   ) { rs -> rs.getLong(1) }.first()

   val usernames = server.storage.query(
       "SELECT username FROM hello_sessions WHERE uuid = ?",
       listOf(player.uuid.toString()),
   ) { rs -> rs.getString("username")!! }

Parameters are passed as a ``List<Any?>`` using JDBC positional binding (``?``).

Writing
-------

``execute`` returns the number of affected rows:

.. code-block:: kotlin

   server.storage.execute(
       "INSERT INTO hello_sessions(uuid, username, joined_at) VALUES (?, ?, ?)",
       listOf(player.uuid.toString(), player.username, System.currentTimeMillis()),
   )

Transactions
------------

``transaction`` holds the database mutex for its entire duration. Inside the
block, use the provided ``TransactionScope`` receiver — it has the same
``query`` / ``execute`` methods but bypasses the outer lock:

.. code-block:: kotlin

   server.storage.transaction {
       val existing = query(
           "SELECT id FROM accounts WHERE uuid = ?",
           listOf(player.uuid.toString()),
       ) { rs -> rs.getInt("id") }

       if (existing.isEmpty()) {
           execute(
               "INSERT INTO accounts(uuid, coins) VALUES (?, 0)",
               listOf(player.uuid.toString()),
           )
       }
   }

.. note::

   Keep transactions short. The mutex blocks all other plugin database operations
   for the duration of the block. Long-running work (network calls, ``delay``)
   inside a transaction stalls every other plugin that wants the database.

Full Example — Session Tracking
---------------------------------

Migration file ``src/main/resources/db/migration/V1__init.sql``:

.. code-block:: sql

   CREATE TABLE hello_sessions (
       id        INTEGER PRIMARY KEY AUTOINCREMENT,
       uuid      TEXT    NOT NULL,
       username  TEXT    NOT NULL,
       joined_at INTEGER NOT NULL
   );

Plugin code:

.. code-block:: kotlin

   class HelloWorldPlugin : VectorPlugin({

       onEnable {
           migrate()
           val lifetime = server.storage.query(
               "SELECT count(*) FROM hello_sessions"
           ) { rs -> rs.getLong(1) }.first()
           logger.info("{} lifetime session(s) on record.", lifetime)
       }

       on<PlayerJoinEvent> { event ->
           server.storage.execute(
               "INSERT INTO hello_sessions(uuid, username, joined_at) VALUES (?, ?, ?)",
               listOf(
                   event.player.uuid.toString(),
                   event.player.username,
                   System.currentTimeMillis(),
               ),
           )
       }
   })

Configuration
-------------

.. code-block:: toml

   [storage]
   file = "data/vector.db"   # path to the SQLite database file

The ``data/`` directory is created automatically if it does not exist.

Pluggable Backends
------------------

``StorageBackend`` is an interface. A future backend (PostgreSQL, MySQL) only
needs to implement ``query``, ``execute``, ``transaction``, ``migrate``, and
``close``. Plugin code that uses only ``server.storage`` will work without
modification on any backend.

.. mermaid::

   graph LR
       Plugin["Plugin code\nserver.storage.query(...)"]

       subgraph API["vector-api"]
           SB["StorageBackend\n(interface)"]
       end

       subgraph Proxy["vector-proxy"]
           SQLite["SqliteStorageBackend\nJDBC + Flyway"]
           PG["PostgresStorageBackend\n(future)"]
       end

       Plugin --> SB
       SB --> SQLite
       SB -.->|future| PG

       style SB fill:#d9edf7,stroke:#31708f
       style SQLite fill:#dff0d8,stroke:#3c763d
       style PG fill:#f5f5f5,stroke:#aaa,stroke-dasharray:4 4
