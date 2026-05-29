package dev.vector.vectest

import dev.vector.api.kotlin.VectorPlugin
import dev.vector.compat.VelocityCommandManagerShim
import dev.vector.compat.VelocityEventManagerShim
import dev.vector.compat.VelocityPluginManagerShim
import dev.vector.compat.VelocityProxyServerShim
import dev.vector.compat.VelocitySchedulerShim
import dev.vector.vectest.tests.VelocityCommandManagerTests
import dev.vector.vectest.tests.VelocityEventManagerTests
import dev.vector.vectest.tests.VelocityPlayerTests
import dev.vector.vectest.tests.VelocityProxyServerTests
import dev.vector.vectest.tests.VelocitySchedulerTests
import dev.vector.vectest.tests.VectorApiTests

class VecTestPlugin : VectorPlugin({
    onEnable {
        logger.info("[VecTest] starting...")

        val suite = VecTestSuite()

        val eventMgr = VelocityEventManagerShim(server)
        val cmdMgr = VelocityCommandManagerShim(server)
        val sched = VelocitySchedulerShim()
        val pluginMgr = VelocityPluginManagerShim(server)
        val proxyShim = VelocityProxyServerShim(server, eventMgr, cmdMgr, sched, pluginMgr)

        VectorApiTests(suite, server).run()
        VelocityProxyServerTests(suite, proxyShim, server).run()
        VelocityPlayerTests(suite, proxyShim, server).run()
        VelocityEventManagerTests(suite, eventMgr).run()
        VelocityCommandManagerTests(suite, cmdMgr).run()
        VelocitySchedulerTests(suite, sched).run()

        sched.shutdown()

        suite.failures().forEach { (name, err) ->
            logger.error("[VecTest] FAIL: {} — {}", name, err?.message ?: err?.javaClass?.simpleName)
        }
        val total = suite.passed + suite.failed
        if (suite.failed == 0) {
            logger.info("[VecTest] ALL {}/{} PASSED", suite.passed, total)
        } else {
            logger.error("[VecTest] FAILED: {}/{} passed, {} failed — see above",
                suite.passed, total, suite.failed)
        }
    }
})
