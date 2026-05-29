package dev.vector.vectest.tests

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import dev.vector.compat.VelocityEventManagerShim
import dev.vector.vectest.MockVectorPlayer
import dev.vector.vectest.VecTestSuite
import java.util.concurrent.atomic.AtomicInteger

class VelocityEventManagerTests(
    private val s: VecTestSuite,
    private val eventMgr: VelocityEventManagerShim,
) {
    class VecTestEvent(val value: String)

    fun run() {
        val plugin = Object()

        // - @Subscribe registration and dispatch
        val subscribeCount = AtomicInteger(0)
        val listener = object : Any() {
            @Subscribe
            fun onVecTest(event: VecTestEvent) { subscribeCount.incrementAndGet() }
        }
        s.check("EventManager.register(@Subscribe) no throw") {
            eventMgr.register(plugin, listener)
        }
        s.check("EventManager.fire dispatches to @Subscribe listener") {
            subscribeCount.set(0)
            eventMgr.fire(VecTestEvent("hello"))
            s.assert(subscribeCount.get() == 1, "expected 1 call, got ${subscribeCount.get()}")
        }
        s.check("EventManager.unregisterListeners — listener no longer called") {
            eventMgr.unregisterListeners(plugin)
            subscribeCount.set(0)
            eventMgr.fire(VecTestEvent("after-unregister"))
            s.assert(subscribeCount.get() == 0, "expected 0 calls after unregister, got ${subscribeCount.get()}")
        }

        // - Functional EventHandler registration
        val funcCount = AtomicInteger(0)
        val handler = com.velocitypowered.api.event.EventHandler<VecTestEvent> { funcCount.incrementAndGet() }
        s.check("EventManager.register(functional EventHandler) no throw") {
            eventMgr.register(plugin, VecTestEvent::class.java, PostOrder.NORMAL, handler)
        }
        s.check("EventManager.fire dispatches to functional handler") {
            funcCount.set(0)
            eventMgr.fire(VecTestEvent("func"))
            s.assert(funcCount.get() == 1, "expected 1 call, got ${funcCount.get()}")
        }
        s.check("EventManager.unregister(functional handler) no throw") {
            eventMgr.unregister(plugin, handler)
        }
        s.check("EventManager.fire after unregister — functional handler not called") {
            funcCount.set(0)
            eventMgr.fire(VecTestEvent("after"))
            s.assert(funcCount.get() == 0, "expected 0 calls, got ${funcCount.get()}")
        }

        // - fire returns the event
        s.check("EventManager.fire returns CompletableFuture with event") {
            val event = VecTestEvent("roundtrip")
            val future = eventMgr.fire(event)
            s.assert(!future.isCompletedExceptionally)
            s.assertEquals(event, future.get())
        }

        // - fireAndForget
        s.check("EventManager.fireAndForget no throw") {
            eventMgr.fireAndForget(VecTestEvent("fire-forget"))
        }

        // - PostOrder ordering: LAST fires before FIRST (descending ordinal)
        val order = mutableListOf<String>()
        val listenerA = object : Any() {
            @Subscribe(order = PostOrder.FIRST)
            fun onA(event: VecTestEvent) { order += "FIRST" }
        }
        val listenerB = object : Any() {
            @Subscribe(order = PostOrder.LAST)
            fun onB(event: VecTestEvent) { order += "LAST" }
        }
        s.check("EventManager PostOrder — LAST fires before FIRST") {
            eventMgr.register(plugin, listenerA)
            eventMgr.register(plugin, listenerB)
            order.clear()
            eventMgr.fire(VecTestEvent("ordering"))
            eventMgr.unregisterListeners(plugin)
            val lastIdx = order.indexOf("LAST")
            val firstIdx = order.indexOf("FIRST")
            s.assert(lastIdx < firstIdx, "expected LAST before FIRST, got: $order")
        }

        // - unregisterListener (per listener)
        val plug2 = Object()
        val perListenerCount = AtomicInteger(0)
        val listenerC = object : Any() {
            @Subscribe
            fun onC(event: VecTestEvent) { perListenerCount.incrementAndGet() }
        }
        s.check("EventManager.unregisterListener — removes only that listener") {
            eventMgr.register(plug2, listenerC)
            eventMgr.unregisterListener(plug2, listenerC)
            perListenerCount.set(0)
            eventMgr.fire(VecTestEvent("per-listener"))
            s.assert(perListenerCount.get() == 0)
        }
    }
}
