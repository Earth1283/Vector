package dev.vector.proxy.protocol

import dev.vector.proxy.protocol.packet.MinecraftPacket
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class DirectionRegistry {
    // TreeMap<protocolInt, Map<packetId, factory>> — floor lookup gives nearest-lower-version match
    private val byId = TreeMap<Int, MutableMap<Int, () -> MinecraftPacket>>()
    private val byClass = TreeMap<Int, MutableMap<KClass<*>, Int>>()

    // Factory cache: Long key = (packetId << 32) | version.ordinal.
    // Uses a sentinel lambda (ABSENT) instead of nullable to avoid a containsKey double-lookup.
    private val ABSENT: () -> MinecraftPacket = { error("absent sentinel") }
    private val factoryCache = ConcurrentHashMap<Long, () -> MinecraftPacket>()

    // ID cache: per-class IntArray indexed by ProtocolVersion.ordinal.
    // Sentinel values: NOT_COMPUTED = Int.MIN_VALUE, NOT_FOUND = -1.
    // IntArray writes are benign-racy: the computation is deterministic so concurrent stores
    // always produce the same value, making double-write harmless.
    private val NOT_COMPUTED = Int.MIN_VALUE
    private val NOT_FOUND = -1
    private val idCacheArr = ConcurrentHashMap<KClass<*>, IntArray>()

    fun register(
        clazz: KClass<out MinecraftPacket>,
        sinceVersion: ProtocolVersion,
        id: Int,
        factory: () -> MinecraftPacket,
    ) {
        byId.getOrPut(sinceVersion.protocol) { mutableMapOf() }[id] = factory
        byClass.getOrPut(sinceVersion.protocol) { mutableMapOf() }[clazz] = id
        factoryCache.clear()
        idCacheArr.clear()
    }

    // Walk all registered versions ≤ requested in ascending order; later entries override earlier.
    fun getFactory(packetId: Int, version: ProtocolVersion): (() -> MinecraftPacket)? {
        val key = (packetId.toLong() shl 32) or version.ordinal.toLong()
        val cached = factoryCache[key]
        if (cached != null) return if (cached === ABSENT) null else cached

        var result: (() -> MinecraftPacket)? = null
        byId.headMap(version.protocol, true).values.forEach { map -> map[packetId]?.also { result = it } }
        val toStore = result ?: ABSENT
        factoryCache[key] = toStore
        return result
    }

    fun getPacketId(clazz: KClass<*>, version: ProtocolVersion): Int? {
        val arr = idCacheArr.computeIfAbsent(clazz) {
            IntArray(ProtocolVersion.entries.size) { NOT_COMPUTED }
        }
        val idx = version.ordinal
        val v = arr[idx]
        if (v != NOT_COMPUTED) return if (v == NOT_FOUND) null else v

        var result = NOT_FOUND
        byClass.headMap(version.protocol, true).values.forEach { map -> map[clazz]?.also { result = it } }
        arr[idx] = result
        return if (result == NOT_FOUND) null else result
    }
}
