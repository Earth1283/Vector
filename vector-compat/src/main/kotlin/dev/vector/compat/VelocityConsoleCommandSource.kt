package dev.vector.compat

import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConsoleCommandSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory

class VelocityConsoleCommandSource : ConsoleCommandSource {
    private val logger = LoggerFactory.getLogger("Proxy")

    override fun getPermissionValue(permission: String): Tristate = Tristate.TRUE
    
    override fun sendMessage(message: Component) {
        val plain = PlainTextComponentSerializer.plainText().serialize(message)
        logger.info(plain)
    }
}
