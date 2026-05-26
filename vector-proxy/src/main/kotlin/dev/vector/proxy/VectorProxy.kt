package dev.vector.proxy

import dev.vector.proxy.config.VectorConfig
import dev.vector.proxy.console.JLineAppender
import dev.vector.proxy.console.ProxyConsole
import dev.vector.proxy.console.defaultTheme
import dev.vector.proxy.network.NettyTransport
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Vector")

fun main() {
    val console = ProxyConsole(defaultTheme())
    JLineAppender.attach(console)

    Runtime.getRuntime().addShutdownHook(Thread({
        JLineAppender.detach()
        console.shutdown()
    }, "console-shutdown"))

    logger.info("Vector {} starting", VERSION)
    logger.info("Java {} ({}) — {} {}", System.getProperty("java.version"), System.getProperty("java.vendor"),
        System.getProperty("os.name"), System.getProperty("os.arch"))
    logger.info("Transport: {}", when {
        NettyTransport.isEpoll  -> "Epoll (Linux native)"
        NettyTransport.isKQueue -> "KQueue (macOS native)"
        else                    -> "NIO (portable fallback)"
    })

    val config = VectorConfig.load()
    val server = VectorServer(config, console)

    console.startReadLoop(server.proxyScope) { line ->
        server.handleConsoleCommand(line)
    }

    server.start()
}

private const val VERSION = "1.0.0-SNAPSHOT"
