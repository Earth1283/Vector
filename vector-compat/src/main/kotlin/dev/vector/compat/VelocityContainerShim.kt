package dev.vector.compat

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginDescription
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VelocityContainerShim(
    private val description: PluginDescription,
) : PluginContainer {

    private var instance: Any? = null

    @Volatile
    private var executor: ExecutorService? = null

    fun setInstance(obj: Any) {
        instance = obj
    }

    override fun getDescription(): PluginDescription = description

    override fun getInstance(): Optional<*> = Optional.ofNullable(instance)

    override fun getExecutorService(): ExecutorService {
        if (executor == null) {
            synchronized(this) {
                if (executor == null) {
                    val name = description.getName().orElse(description.getId())
                    executor = Executors.unconfigurableExecutorService(
                        Executors.newCachedThreadPool(
                            ThreadFactoryBuilder()
                                .setDaemon(true)
                                .setNameFormat("$name - Task Executor #%d")
                                .build()
                        )
                    )
                }
            }
        }
        return executor!!
    }
}
