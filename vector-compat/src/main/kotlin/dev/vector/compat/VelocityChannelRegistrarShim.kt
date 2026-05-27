package dev.vector.compat

import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.ChannelRegistrar

class VelocityChannelRegistrarShim : ChannelRegistrar {
    override fun register(vararg identifiers: ChannelIdentifier) {}
    override fun unregister(vararg identifiers: ChannelIdentifier) {}
}
