package dev.vector.vectest.tests

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.RawCommand
import com.velocitypowered.api.command.SimpleCommand
import dev.vector.compat.VelocityCommandManagerShim
import dev.vector.compat.VelocityConsoleCommandSource
import dev.vector.vectest.VecTestSuite
import java.util.concurrent.atomic.AtomicInteger

class VelocityCommandManagerTests(
    private val s: VecTestSuite,
    private val cmdMgr: VelocityCommandManagerShim,
) {
    fun run() {
        // - metaBuilder
        s.check("CommandManager.metaBuilder(alias) non-null") {
            s.assertNotNull(cmdMgr.metaBuilder("vectest-cmd"))
        }
        s.check("CommandManager.metaBuilder(alias).build() has alias") {
            val meta = cmdMgr.metaBuilder("vectest-meta").build()
            s.assert(meta.aliases.contains("vectest-meta"))
        }
        s.check("CommandManager.metaBuilder(alias).aliases(...) adds extra aliases") {
            val meta = cmdMgr.metaBuilder("vectest-main").aliases("vectest-alt").build()
            s.assert(meta.aliases.contains("vectest-alt"))
        }

        // - RawCommand registration
        val rawExecCount = AtomicInteger(0)
        val rawCmd = RawCommand { rawExecCount.incrementAndGet() }
        val rawMeta = cmdMgr.metaBuilder("vectest-raw").build()
        s.check("CommandManager.register(meta, RawCommand) no throw") {
            cmdMgr.register(rawMeta, rawCmd)
        }

        // - SimpleCommand registration
        val simpleExecCount = AtomicInteger(0)
        val simpleCmd = object : SimpleCommand {
            override fun execute(inv: SimpleCommand.Invocation) { simpleExecCount.incrementAndGet() }
        }
        val simpleMeta = cmdMgr.metaBuilder("vectest-simple").build()
        s.check("CommandManager.register(meta, SimpleCommand) no throw") {
            cmdMgr.register(simpleMeta, simpleCmd)
        }

        // - BrigadierCommand registration
        val brigExecCount = AtomicInteger(0)
        val brigNode = LiteralArgumentBuilder.literal<CommandSource>("vectest-brig")
            .executes { brigExecCount.incrementAndGet(); 1 }
            .build()
        val brigCmd = BrigadierCommand(brigNode)
        s.check("CommandManager.metaBuilder(BrigadierCommand) non-null") {
            s.assertNotNull(cmdMgr.metaBuilder(brigCmd))
        }
        s.check("CommandManager.register(BrigadierCommand) no throw") {
            cmdMgr.register(brigCmd)
        }

        // - Stubs
        val console = VelocityConsoleCommandSource()
        s.check("CommandManager.executeAsync returns CompletableFuture") {
            val future = cmdMgr.executeAsync(console, "vectest-raw")
            s.assertNotNull(future)
        }
        s.check("CommandManager.executeImmediatelyAsync returns CompletableFuture") {
            val future = cmdMgr.executeImmediatelyAsync(console, "vectest-raw")
            s.assertNotNull(future)
        }
        s.check("CommandManager.hasCommand returns Boolean") {
            cmdMgr.hasCommand("vectest-raw") // stub — just must not throw
        }
        s.check("CommandManager.getAliases returns Collection") {
            s.assertNotNull(cmdMgr.aliases)
        }
        s.check("CommandManager.getCommandMeta returns null or meta (stub)") {
            cmdMgr.getCommandMeta("vectest-raw") // stub — null is valid
        }

        // - unregister
        s.check("CommandManager.unregister(alias) no throw") {
            cmdMgr.unregister("vectest-raw")
        }
        s.check("CommandManager.unregister(meta) no throw") {
            cmdMgr.unregister(rawMeta)
        }

        // - VelocityCommandManagerShimDelegator
        s.check("VelocityCommandManagerShimDelegator delegates register") {
            val delegator = dev.vector.compat.VelocityCommandManagerShimDelegator(cmdMgr, "vectest")
            val dMeta = delegator.metaBuilder("vectest-deleg").build()
            delegator.register(dMeta, rawCmd) // must not throw
        }
        s.check("VelocityCommandManagerShimDelegator delegates register(BrigadierCommand)") {
            val delegator = dev.vector.compat.VelocityCommandManagerShimDelegator(cmdMgr, "vectest")
            val node2 = LiteralArgumentBuilder.literal<CommandSource>("vectest-deleg-brig").build()
            delegator.register(BrigadierCommand(node2))
        }
    }
}
