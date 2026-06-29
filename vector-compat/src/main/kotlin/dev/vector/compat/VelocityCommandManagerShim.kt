package dev.vector.compat

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.Command
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.RawCommand
import com.velocitypowered.api.command.SimpleCommand
import dev.vector.api.ProxyServer
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.asContextElement

val currentCommandSender = ThreadLocal<String?>()

class VelocityCommandManagerShim(private val vectorServer: ProxyServer) : CommandManager {

    private val brigadierDispatchers = java.util.concurrent.ConcurrentHashMap<Command, CommandDispatcher<CommandSource>>()

    override fun metaBuilder(alias: String): CommandMeta.Builder =
        VelocityCommandMetaBuilder(alias)

    override fun metaBuilder(command: BrigadierCommand): CommandMeta.Builder =
        VelocityCommandMetaBuilder(command.node.name)

    override fun register(command: BrigadierCommand) {
        val meta = metaBuilder(command).build()
        register(meta, command)
    }

    override fun register(meta: CommandMeta, command: Command) {
        register(meta, command, "velocity-compat")
    }

    fun register(meta: CommandMeta, command: Command, pluginId: String) {
        val aliases = meta.aliases
        if (aliases.isEmpty()) return
        
        if (command is BrigadierCommand) {
            val dispatcher = CommandDispatcher<CommandSource>()
            dispatcher.root.addChild(command.node)
            brigadierDispatchers[command] = dispatcher
        }

        for (alias in aliases) {
            vectorServer.registerCommand(alias, pluginId, { args ->
                val senderName = currentCommandSender.get()
                val source = if (senderName != null) {
                    val vectorPlayer = vectorServer.getPlayer(senderName)
                    if (vectorPlayer != null) {
                        VelocityPlayerShim(vectorPlayer, vectorServer)
                    } else {
                        VelocityConsoleCommandSource()
                    }
                } else {
                    VelocityConsoleCommandSource()
                }
                try {
                    when (command) {
                        is RawCommand -> {
                            val invocation = VelocityRawInvocation(source, alias, args.joinToString(" "))
                            command.execute(invocation)
                        }
                        is SimpleCommand -> {
                            val invocation = VelocitySimpleInvocation(source, alias, args.toTypedArray())
                            command.execute(invocation)
                        }
                        is BrigadierCommand -> {
                            val dispatcher = brigadierDispatchers[command]
                            val cmdLine = if (args.isEmpty()) alias else "$alias ${args.joinToString(" ")}"
                            dispatcher?.execute(cmdLine, source)
                        }
                    }
                } catch (e: Exception) {
                    source.sendMessage(net.kyori.adventure.text.Component.text("Error executing command: ${e.message}", net.kyori.adventure.text.format.NamedTextColor.RED))
                }
            }, { args ->
                val senderName = currentCommandSender.get()
                val source = if (senderName != null) {
                    val vectorPlayer = vectorServer.getPlayer(senderName)
                    if (vectorPlayer != null) {
                        VelocityPlayerShim(vectorPlayer, vectorServer)
                    } else {
                        VelocityConsoleCommandSource()
                    }
                } else {
                    VelocityConsoleCommandSource()
                }
                when (command) {
                    is RawCommand -> {
                        val invocation = VelocityRawInvocation(source, alias, args.joinToString(" "))
                        command.suggest(invocation)
                    }
                    is SimpleCommand -> {
                        val invocation = VelocitySimpleInvocation(source, alias, args.toTypedArray())
                        command.suggest(invocation)
                    }
                    is BrigadierCommand -> {
                        val dispatcher = brigadierDispatchers[command] ?: return@registerCommand emptyList()
                        val cmdLine = if (args.isEmpty()) alias else "$alias ${args.joinToString(" ")}"
                        val parseResults = dispatcher.parse(cmdLine, source)
                        val suggestions = dispatcher.getCompletionSuggestions(parseResults).get()
                        suggestions.list.map { it.text }
                    }
                    else -> emptyList()
                }
            })
        }
    }

    override fun unregister(alias: String) {
        vectorServer.unregisterCommand(alias)
    }

    override fun unregister(meta: CommandMeta) {
        meta.aliases.forEach { unregister(it) }
    }

    override fun getCommandMeta(alias: String): CommandMeta? = null

    override fun executeAsync(source: CommandSource, cmd: String): CompletableFuture<Boolean> =
        CompletableFuture.completedFuture(false)

    override fun executeImmediatelyAsync(source: CommandSource, cmd: String): CompletableFuture<Boolean> =
        CompletableFuture.completedFuture(false)

    override fun getAliases(): Collection<String> = emptyList()

    override fun hasCommand(alias: String): Boolean = false

    override fun hasCommand(alias: String, source: CommandSource): Boolean = false

    private class VelocityCommandMeta(
        val primaryAlias: String,
        val otherAliases: List<String>,
        private val pluginInstance: Any?,
    ) : CommandMeta {
        override fun getAliases(): Collection<String> = listOf(primaryAlias) + otherAliases
        override fun getHints(): Collection<CommandNode<CommandSource>> = emptyList()
        override fun getPlugin(): Any? = pluginInstance
    }

    private class VelocityCommandMetaBuilder(val primaryAlias: String) : CommandMeta.Builder {
        private val aliases = mutableListOf<String>()
        private var pluginInstance: Any? = null

        override fun aliases(vararg aliases: String?): CommandMeta.Builder {
            aliases.filterNotNull().forEach { this.aliases.add(it) }
            return this
        }

        override fun hint(node: CommandNode<CommandSource>?): CommandMeta.Builder = this

        override fun plugin(plugin: Any?): CommandMeta.Builder {
            this.pluginInstance = plugin
            return this
        }

        override fun build(): CommandMeta = VelocityCommandMeta(primaryAlias, aliases, pluginInstance)
    }

    private class VelocityRawInvocation(
        private val source: CommandSource,
        private val alias: String,
        private val args: String,
    ) : RawCommand.Invocation {
        override fun source(): CommandSource = source
        override fun alias(): String = alias
        override fun arguments(): String = args
    }

    private class VelocitySimpleInvocation(
        private val source: CommandSource,
        private val alias: String,
        private val arguments: Array<String>,
    ) : SimpleCommand.Invocation {
        override fun source(): CommandSource = source
        override fun alias(): String = alias
        override fun arguments(): Array<String> = arguments
    }
}

class VelocityCommandManagerShimDelegator(
    private val delegate: VelocityCommandManagerShim,
    private val pluginId: String,
) : CommandManager by delegate {
    override fun register(meta: CommandMeta, command: Command) {
        delegate.register(meta, command, pluginId)
    }
    override fun register(command: BrigadierCommand) {
        val meta = delegate.metaBuilder(command).build()
        delegate.register(meta, command, pluginId)
    }
}
