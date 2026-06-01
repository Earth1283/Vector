package dev.vector.proxy.network

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rejects inbound connections that exceed a global or per-source-IP concurrent limit, mitigating
 * connection-flood / socket-exhaustion DoS. Must sit first in the pipeline so a rejected connection
 * is closed before any decoding or session work happens.
 *
 * One instance is created per channel; all instances share a single [State] holding the counters.
 * A limit of 0 disables that particular check.
 */
class ConnectionLimiter(
    private val state: State,
    private val maxPerIp: Int,
    private val maxTotal: Int,
) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(ConnectionLimiter::class.java)

    // Per-channel accounting flags so channelInactive releases exactly what channelActive reserved.
    private var countedTotal = false
    private var countedIp: InetAddress? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ip = (ctx.channel().remoteAddress() as? InetSocketAddress)?.address

        if (maxTotal > 0) {
            if (state.total.incrementAndGet() > maxTotal) {
                state.total.decrementAndGet()
                logger.debug("Rejected connection from {}: global limit ({}) reached", ip, maxTotal)
                ctx.close()
                return
            }
            countedTotal = true
        }

        if (maxPerIp > 0 && ip != null) {
            // Reserve a slot atomically: the read, limit check, and increment all run inside
            // compute() under the bin lock, so concurrent connects from one IP cannot overshoot
            // the limit or lose updates against the prune in release().
            var accepted = false
            state.perIp.compute(ip) { _, existing ->
                val counter = existing ?: AtomicInteger(0)
                if (counter.get() < maxPerIp) {
                    counter.incrementAndGet()
                    accepted = true
                }
                counter
            }
            if (!accepted) {
                releaseTotal()
                logger.debug("Rejected connection from {}: per-IP limit ({}) reached", ip, maxPerIp)
                ctx.close()
                return
            }
            countedIp = ip
        }

        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        release()
        super.channelInactive(ctx)
    }

    private fun release() {
        releaseTotal()
        val ip = countedIp ?: return
        countedIp = null
        // Decrement under the bin lock and prune the entry at zero to keep the map bounded.
        // Our held +1 keeps the counter >= 1 until now, so the entry/object is stable here.
        state.perIp.compute(ip) { _, existing ->
            if (existing == null || existing.decrementAndGet() <= 0) null else existing
        }
    }

    private fun releaseTotal() {
        if (countedTotal) {
            countedTotal = false
            state.total.decrementAndGet()
        }
    }

    /** Shared counters across all channels. Create one and pass it to every [ConnectionLimiter]. */
    class State {
        val total = AtomicInteger(0)
        val perIp = ConcurrentHashMap<InetAddress, AtomicInteger>()
    }
}
