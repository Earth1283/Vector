package dev.vector.proxy.protocol

import dev.vector.proxy.protocol.packet.MinecraftPacket
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class DirectionRegistry {
    // TreeMap<protocolInt, Map<packetId, factory>> — floor lookup gives nearest-lower-version match
    private val byId = TreeMap<Int, MutableMap<Int, () -> MinecraftPacket>>()
    private val byClass = TreeMap<Int, MutableMap<KClass<*>, Int>>()

    private val factoryCache = ConcurrentHashMap<Long, (() -> MinecraftPacket)?>()
    private val idCache = ConcurrentHashMap<Pair<KClass<*>, ProtocolVersion>, Int?>()

    fun register(
        clazz: KClass<out MinecraftPacket>,
        sinceVersion: ProtocolVersion,
        id: Int,
        factory: () -> MinecraftPacket,
    ) {
        byId.getOrPut(sinceVersion.protocol) { mutableMapOf() }[id] = factory
        byClass.getOrPut(sinceVersion.protocol) { mutableMapOf() }[clazz] = id
        factoryCache.clear()
        idCache.clear()
    }

    // Walk all registered versions ≤ requested in ascending order; later entries override earlier.
    // This lets us register a new packet ID at a higher version without losing lower-version entries.
    fun getFactory(packetId: Int, version: ProtocolVersion): (() -> MinecraftPacket)? {
        val key = (packetId.toLong() shl 32) or (version.ordinal.toLong())
        val cached = factoryCache[key]
        if (cached != null || factoryCache.containsKey(key)) {
            return cached
        }
        var result: (() -> MinecraftPacket)? = null
        byId.headMap(version.protocol, true).values.forEach { map -> map[packetId]?.also { result = it } }
        factoryCache[key] = result
        return result
    }

    fun getPacketId(clazz: KClass<*>, version: ProtocolVersion): Int? {
        val key = Pair(clazz, version)
        val cached = idCache[key]
        if (cached != null || idCache.containsKey(key)) {
            return cached
        }
        var result: Int? = null
        byClass.headMap(version.protocol, true).values.forEach { map -> map[clazz]?.also { result = it } }
        idCache[key] = result
        return result
    }
}
