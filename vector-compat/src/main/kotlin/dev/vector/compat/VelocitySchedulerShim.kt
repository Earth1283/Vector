package dev.vector.compat

import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.api.scheduler.Scheduler
import com.velocitypowered.api.scheduler.TaskStatus
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class VelocitySchedulerShim : Scheduler {

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(4)

    override fun buildTask(plugin: Any, runnable: Runnable): Scheduler.TaskBuilder =
        TaskBuilderImpl(plugin) { runnable.run() }

    override fun buildTask(plugin: Any, consumer: Consumer<ScheduledTask>): Scheduler.TaskBuilder =
        TaskBuilderImpl(plugin) { task -> consumer.accept(task) }

    override fun tasksByPlugin(plugin: Any): Collection<ScheduledTask> = emptyList()

    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
        }
    }

    private inner class TaskBuilderImpl(
        private val ownerPlugin: Any,
        private val action: (ScheduledTask) -> Unit,
    ) : Scheduler.TaskBuilder {

        private var delayMs: Long = 0L
        private var repeatMs: Long = 0L

        override fun delay(time: Long, unit: TimeUnit): Scheduler.TaskBuilder {
            delayMs = unit.toMillis(time); return this
        }

        override fun repeat(time: Long, unit: TimeUnit): Scheduler.TaskBuilder {
            repeatMs = unit.toMillis(time); return this
        }

        override fun clearDelay(): Scheduler.TaskBuilder { delayMs = 0; return this }
        override fun clearRepeat(): Scheduler.TaskBuilder { repeatMs = 0; return this }

        override fun schedule(): ScheduledTask {
            val task = TaskImpl(ownerPlugin)
            val future: Future<*> = if (repeatMs > 0) {
                executor.scheduleAtFixedRate(
                    { if (task.status() == TaskStatus.SCHEDULED) action(task) },
                    delayMs, repeatMs, TimeUnit.MILLISECONDS,
                )
            } else {
                executor.schedule(
                    { if (task.status() == TaskStatus.SCHEDULED) action(task) },
                    delayMs, TimeUnit.MILLISECONDS,
                )
            }
            task.setFuture(future)
            return task
        }
    }

    private class TaskImpl(private val ownerPlugin: Any) : ScheduledTask {
        private val statusRef = AtomicReference(TaskStatus.SCHEDULED)
        private var future: Future<*>? = null

        fun setFuture(f: Future<*>) { future = f }

        override fun plugin(): Any = ownerPlugin
        override fun status(): TaskStatus = statusRef.get()

        override fun cancel() {
            statusRef.set(TaskStatus.CANCELLED)
            future?.cancel(false)
        }
    }
}
