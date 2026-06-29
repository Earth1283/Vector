package dev.vector.vectest.tests

import com.velocitypowered.api.scheduler.TaskStatus
import dev.vector.compat.VelocitySchedulerShim
import dev.vector.vectest.VecTestSuite
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VelocitySchedulerTests(
    private val s: VecTestSuite,
    private val sched: VelocitySchedulerShim,
) {
    private val plugin = Object()

    fun run() {
        // - buildTask(plugin, Runnable)
        s.check("Scheduler.buildTask(Runnable) returns TaskBuilder") {
            s.assertNotNull(sched.buildTask(plugin, Runnable {}))
        }
        s.check("Scheduler.TaskBuilder.schedule() returns ScheduledTask") {
            val task = sched.buildTask(plugin, Runnable {}).delay(1, TimeUnit.HOURS).schedule()
            s.assertNotNull(task)
            task.cancel()
        }
        s.check("Scheduler.ScheduledTask.plugin() non-null") {
            val task = sched.buildTask(plugin, Runnable {}).delay(1, TimeUnit.HOURS).schedule()
            s.assertNotNull(task.plugin())
            task.cancel()
        }
        s.check("Scheduler.ScheduledTask.status() SCHEDULED before cancel") {
            val task = sched.buildTask(plugin, Runnable {}).delay(1, TimeUnit.HOURS).schedule()
            s.assertEquals(TaskStatus.SCHEDULED, task.status())
            task.cancel()
        }
        s.check("Scheduler.ScheduledTask.cancel() sets status CANCELLED") {
            val task = sched.buildTask(plugin, Runnable {}).delay(1, TimeUnit.HOURS).schedule()
            task.cancel()
            s.assertEquals(TaskStatus.CANCELLED, task.status())
        }

        // - buildTask(plugin, Consumer<ScheduledTask>)
        val consumerInvoked = AtomicBoolean(false)
        s.check("Scheduler.buildTask(Consumer) returns TaskBuilder") {
            s.assertNotNull(sched.buildTask(plugin, java.util.function.Consumer { consumerInvoked.set(true) }))
        }

        // - Repeating task (short interval, cancel immediately)
        s.check("Scheduler.TaskBuilder.repeat() schedules repeating task") {
            val task = sched.buildTask(plugin, Runnable {})
                .delay(1, TimeUnit.HOURS)
                .repeat(1, TimeUnit.HOURS)
                .schedule()
            s.assertEquals(TaskStatus.SCHEDULED, task.status())
            task.cancel()
            s.assertEquals(TaskStatus.CANCELLED, task.status())
        }

        // - clearDelay / clearRepeat
        s.check("Scheduler.TaskBuilder.clearDelay() no throw") {
            sched.buildTask(plugin, Runnable {}).delay(5, TimeUnit.SECONDS).clearDelay()
        }
        s.check("Scheduler.TaskBuilder.clearRepeat() no throw") {
            sched.buildTask(plugin, Runnable {}).repeat(5, TimeUnit.SECONDS).clearRepeat()
        }

        // - tasksByPlugin
        s.check("Scheduler.tasksByPlugin returns Collection") {
            s.assertNotNull(sched.tasksByPlugin(plugin))
        }

        // - PluginManager
        s.check("PluginManager.getPlugins returns Collection") {
            val pm = dev.vector.compat.VelocityPluginManagerShim(
                object : dev.vector.api.ProxyServer {
                    override val version = "test"
                    override val eventBus get() = throw UnsupportedOperationException()
                    override val storage get() = throw UnsupportedOperationException()
                    override val players = emptyList<dev.vector.api.VectorPlayer>()
                    override val servers = emptyList<dev.vector.api.BackendServer>()
                    override val coroutineScope get() = kotlinx.coroutines.GlobalScope
                    override fun getPlayer(uuid: java.util.UUID) = null
                    override fun getPlayer(username: String) = null
                    override fun registerCommand(
                        name: String, pluginId: String,
                        handler: suspend (List<String>) -> Unit,
                        completer: (List<String>) -> List<String>
                    ) {}
                    override fun unregisterCommand(name: String) {}
                    override fun unregisterCommands(pluginId: String) {}
                    override fun registerServer(name: String, address: java.net.InetSocketAddress) =
                        throw UnsupportedOperationException()
                    override fun unregisterServer(name: String) {}
                }
            )
            s.assertNotNull(pm.plugins)
            s.assert(pm.plugins.isEmpty())
            s.assert(!pm.isLoaded("nonexistent"))
            s.assert(pm.getPlugin("nonexistent").isEmpty)
        }
    }
}
