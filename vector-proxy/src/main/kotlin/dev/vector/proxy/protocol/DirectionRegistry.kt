package dev.vector.proxy.protocol

import dev.vector.proxy.protocol.packet.MinecraftPacket
import java.util.TreeMap
import kotlin.reflect.KClass

class DirectionRegistry {
    // TreeMap<protocolInt, Map<packetId, factory>> — floor lookup gives nearest-lower-version match
    private val byId = TreeMap<Int, MutableMap<Int, () -> MinecraftPacket>>()
    private val byClass = TreeMap<Int, MutableMap<KClass<*>, Int>>()

    fun register(
        clazz: KClass<out MinecraftPacket>,
        sinceVersion: ProtocolVersion,
        id: Int,
        factory: () -> MinecraftPacket,
    ) {
        byId.getOrPut(sinceVersion.protocol) { mutableMapOf() }[id] = factory
        byClass.getOrPut(sinceVersion.protocol) { mutableMapOf() }[clazz] = id
    }

    fun getFactory(packetId: Int, version: ProtocolVersion): (() -> MinecraftPacket)? =
        byId.floorEntry(version.protocol)?.value?.get(packetId)

    fun getPacketId(clazz: KClass<*>, version: ProtocolVersion): Int? =
        byClass.floorEntry(version.protocol)?.value?.get(clazz)
}
