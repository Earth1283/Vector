Console Commands
================

Vector ships an interactive terminal (jline3) with frecency-ranked tab
completion and inline ghost-text suggestions. All commands are entered at the
``>`` prompt. Tab completes command names and context-sensitive arguments.

players
-------

List all online players with their UUID and current backend server.

.. code-block:: text

   > players
   2 player(s) online:
     Alice | 550e8400-e29b-41d4-a716-446655440000 | server=lobby
     Bob   | 6ba7b810-9dad-11d1-80b4-00c04fd430c8 | server=survival

kick
----

Disconnect a player or all players with an optional reason.

.. code-block:: text

   > kick <player|all> [reason]

Examples:

.. code-block:: text

   > kick Alice
   > kick Alice Goodbye!
   > kick all Server restarting

Tab completion offers online player names and ``all`` as the first argument.
Reason defaults to ``Kicked by operator`` if omitted.

servers
-------

List all configured backend servers. Accepts an optional probe argument to
check TCP reachability.

.. code-block:: text

   > servers
   > servers probe
   > servers --probe
   > servers -p

Without ``probe``, prints each server's address and current player count:

.. code-block:: text

   3 backend server(s):
     lobby     → localhost:25566  (3 player(s)) [default]
     survival  → localhost:25567  (1 player(s))
     creative  → localhost:25568  (0 player(s))

With ``probe``, performs a TCP connect to every server in parallel and
colors each result green (online) or red (offline):

.. code-block:: text

   ● lobby     → localhost:25566  (3 online [default])  [online  12ms]
   ● survival  → localhost:25567  (1 online)            [online  8ms]
   ○ creative  → localhost:25568  (0 online)            [offline]

serverctl
---------

Administrative sugar for backend server management, in the spirit of
``systemctl``. Four subcommands are available.

.. code-block:: text

   > serverctl <list|status|enable|disable> [server]

Tab completes the subcommand at position 1 and server name at position 2.

serverctl list
~~~~~~~~~~~~~~

Tabular view of all configured servers, styled after ``systemctl list-units``.

.. code-block:: text

   > serverctl list
     UNIT              ADDRESS                  PLAYERS  LOAD
     ● lobby.service   localhost:25566              3    enabled (default)
     ● survival.service localhost:25567             1    enabled
     ○ creative.service localhost:25568             0    disabled

``●`` indicates enabled; ``○`` indicates disabled (excluded from routing).

serverctl status
~~~~~~~~~~~~~~~~

Probe a single server and show a detailed status view.

.. code-block:: text

   > serverctl status lobby
   ● lobby.service — localhost:25566
      Active:  active (running)  12ms
      Players: 3 online
      Load:    enabled; preset default

If the server is unreachable:

.. code-block:: text

   ● creative.service — localhost:25568
      Active:  inactive (dead)
      Players: 0 online
      Load:    disabled

serverctl enable
~~~~~~~~~~~~~~~~

Add a server back into the runtime routing pool. Affects ``getInitialServer()``
immediately — the next connecting player can be routed to this server.

.. code-block:: text

   > serverctl enable creative
   Synchronizing state for creative.service...
   creative.service: enabled

serverctl disable
~~~~~~~~~~~~~~~~~

Remove a server from the runtime routing pool. Existing connected players are
unaffected; no new players will be routed to this server until it is
re-enabled. The change is runtime-only — it does not persist across restarts.

.. code-block:: text

   > serverctl disable creative
   Removed creative.service from routing pool.
   creative.service: disabled

plugins
-------

List all loaded plugins with their version and ID.

.. code-block:: text

   > plugins
   2 plugin(s) loaded:
     Hello World v1.0.0  (hello-world)
     My Plugin   v2.3.1  (my-plugin)

broadcast
---------

Send a message to all online players.

.. code-block:: text

   > broadcast <message>

The message is sent as a system chat packet to all players currently connected
to a backend server.

.. note::

   In-game chat packet support is fully functional for players in the play
   state. Messages do not reach players currently in limbo or the login process.

version
-------

Show proxy version, Java runtime, and OS information.

.. code-block:: text

   > version
   Vector 1.0.0-SNAPSHOT (Minecraft proxy)
   Java 21.0.10 — Microsoft
   OS   Linux aarch64
   Transport: Epoll (Linux native)

uptime
------

Show how long the proxy has been running since the last ``start()``.

.. code-block:: text

   > uptime
   Uptime: 2d 4h 17m 33s

stop
----

Gracefully disconnect all players and shut down the proxy.

.. code-block:: text

   > stop
   Stopping proxy (online: 3)...

All connected players receive a ``Proxy shutting down`` disconnect message before
the process exits.

help
----

Print a summary of all available commands. If any plugins have registered console
commands (via the ``command { }`` DSL), they are listed below the built-in section.

.. code-block:: text

   > help
   Available commands:
     players                              — list online players
     kick <player|all> [reason]           — disconnect a player
     servers [probe|--probe|-p]           — list backends; probe pings each one
     serverctl list                       — list backend units (systemctl style)
     serverctl status <server>            — probe and detail a single backend
     serverctl enable|disable <server>    — add/remove backend from routing pool
     plugins                              — list loaded plugins
     broadcast <message>                  — broadcast a message (stub)
     version                              — show version and runtime info
     uptime                               — show how long the proxy has been running
     stop                                 — shut down the proxy gracefully
   Plugin commands:
     hello  (hello-world)
     greet  (hello-world)

Tab Completion
--------------

The console uses a frecency-ranked completer — commands you type more frequently
float to the top of completions over time. Ghost text (inline suggestion after
the cursor) shows the top-ranked completion as you type.

Press ``Tab`` to cycle candidates. Context changes at each word boundary:

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Input
     - Tab offers
   * - ``pla<Tab>``
     - ``players``
   * - ``kick <Tab>``
     - online player names + ``all``
   * - ``servers <Tab>``
     - ``probe``, ``--probe``, ``-p``
   * - ``serverctl <Tab>``
     - ``list``, ``status``, ``enable``, ``disable``
   * - ``serverctl status <Tab>``
     - configured server names
