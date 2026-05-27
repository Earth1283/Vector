package dev.vector.compat

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

class VelocityCommandManagerShim(private val vectorServer: ProxyServer) : CommandManager {

    override fun metaBuilder(alias: String): CommandMeta.Builder =
        VelocityCommandMetaBuilder(alias)

    override fun metaBuilder(command: BrigadierCommand): CommandMeta.Builder =
        throw UnsupportedOperationException("Brigadier metaBuilder not implemented")

    override fun register(command: BrigadierCommand) {
        // Brigadier not yet supported
    }

    override fun register(meta: CommandMeta, command: Command) {
        register(meta, command, "velocity-compat")
    }

    fun register(meta: CommandMeta, command: Command, pluginId: String) {
        val aliases = meta.aliases
        if (aliases.isEmpty()) return
        
        for (alias in aliases) {
            vectorServer.registerCommand(alias, pluginId) { args ->
                val source = VelocityConsoleCommandSource()
                when (command) {
                    is RawCommand -> {
                        val invocation = SimpleRawInvocation(source, alias, args.joinToString(" "))
                        command.execute(invocation)
                    }
                    is SimpleCommand -> {
                        val invocation = SimpleCommandInvocation(source, alias, args.toTypedArray())
                        command.execute(invocation)
                    }
                    else -> {
                        // Other command types (like Brigadier) not yet supported
                    }
                }
            }
        }
    }

    override fun unregister(alias: String) {
        // VectorServer doesn't have a direct unregister for a single alias yet, 
        // it unregisters by plugin ID. We'll leave this as a stub for now.
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

    private class SimpleRawInvocation(
        private val source: CommandSource,
        private val alias: String,
        private val args: String,
    ) : RawCommand.Invocation {
        override fun source(): CommandSource = source
        override fun alias(): String = alias
        override fun arguments(): String = args
    }

    private class SimpleCommandInvocation(
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
}
