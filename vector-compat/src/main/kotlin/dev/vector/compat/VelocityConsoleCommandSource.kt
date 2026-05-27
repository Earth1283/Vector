package dev.vector.compat

import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConsoleCommandSource
import net.kyori.adventure.text.Component

class VelocityConsoleCommandSource : ConsoleCommandSource {
    override fun getPermissionValue(permission: String): Tristate = Tristate.TRUE
    override fun sendMessage(message: Component) {}
}
