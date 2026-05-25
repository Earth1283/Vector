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

    // Walk all registered versions ≤ requested in ascending order; later entries override earlier.
    // This lets us register a new packet ID at a higher version without losing lower-version entries.
    fun getFactory(packetId: Int, version: ProtocolVersion): (() -> MinecraftPacket)? {
        var result: (() -> MinecraftPacket)? = null
        byId.headMap(version.protocol, true).values.forEach { map -> map[packetId]?.also { result = it } }
        return result
    }

    fun getPacketId(clazz: KClass<*>, version: ProtocolVersion): Int? {
        var result: Int? = null
        byClass.headMap(version.protocol, true).values.forEach { map -> map[clazz]?.also { result = it } }
        return result
    }
}
