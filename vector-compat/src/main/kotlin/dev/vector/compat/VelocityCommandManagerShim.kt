package dev.vector.compat

import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.Command
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import dev.vector.api.ProxyServer
import java.util.concurrent.CompletableFuture

class VelocityCommandManagerShim(private val vectorServer: ProxyServer) : CommandManager {

    override fun metaBuilder(alias: String): CommandMeta.Builder =
        throw UnsupportedOperationException("metaBuilder not implemented")

    override fun metaBuilder(command: BrigadierCommand): CommandMeta.Builder =
        throw UnsupportedOperationException("metaBuilder not implemented")

    override fun register(command: BrigadierCommand) {}

    override fun register(meta: CommandMeta, command: Command) {
        val aliases = meta.aliases
        if (aliases.isEmpty()) return
        val primaryAlias = aliases.first()
        vectorServer.registerCommand(primaryAlias, "velocity-compat") { args ->
            val source = VelocityConsoleCommandSource()
            val invocation = SimpleRawInvocation(source, args.joinToString(" "))
            if (command is com.velocitypowered.api.command.RawCommand) {
                command.execute(invocation)
            }
        }
    }

    override fun unregister(alias: String) {}

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

    private class SimpleRawInvocation(
        private val source: CommandSource,
        private val args: String,
    ) : com.velocitypowered.api.command.RawCommand.Invocation {
        override fun source(): CommandSource = source
        override fun alias(): String = ""
        override fun arguments(): String = args
    }
}
