package dev.vector.proxy.console

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.AppenderBase
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import java.text.SimpleDateFormat
import java.util.Date

class JLineAppender : AppenderBase<ILoggingEvent>() {

    override fun append(event: ILoggingEvent) {
        val line = format(event)
        val console = console
        if (console != null) {
            console.printAbove(line)
        } else {
            System.out.println(line)
            System.out.flush()
        }
    }

    private fun format(event: ILoggingEvent): String {
        val theme = console?.theme ?: defaultTheme()
        val levelStyle = when (event.level) {
            Level.ERROR -> theme.error
            Level.WARN  -> theme.warn
            Level.INFO  -> theme.info
            Level.DEBUG -> theme.debug
            else        -> theme.trace
        }

        val ts = TS_FMT.get()!!.format(Date(event.timeStamp))
        val lvl = event.level.toString().padEnd(5)
        val loggerShort = event.loggerName.substringAfterLast('.')

        val asb = AttributedStringBuilder()
            // [HH:mm:ss] timestamp in faint
            .style(AttributedStyle.DEFAULT.faint())
            .append("[$ts] ")
            // Level label with its theme color/weight
            .style(levelStyle.toAttributedStyle())
            .append(lvl)
            // Logger name in faint cyan
            .style(AttributedStyle.DEFAULT.faint().foreground(AttributedStyle.CYAN))
            .append("  $loggerShort")
            // Arrow separator
            .style(AttributedStyle.DEFAULT.faint())
            .append(" › ")
            // Message — colored for WARN/ERROR, default otherwise
            .style(if (levelStyle.colorMessage) levelStyle.toMessageStyle() else AttributedStyle.DEFAULT)
            .append(event.formattedMessage)

        val tp = event.throwableProxy
        if (tp != null) {
            asb.style(AttributedStyle.DEFAULT.faint().foreground(AttributedStyle.RED))
                .append("\n")
                .append(formatThrowable(tp))
        }

        return asb.toAnsi()
    }

    private fun formatThrowable(tp: IThrowableProxy): String {
        val sb = StringBuilder()
        sb.append(tp.className)
        if (tp.message != null) sb.append(": ").append(tp.message)
        val frames = tp.stackTraceElementProxyArray
        frames?.take(8)?.forEach { sb.append("\n\tat ").append(it.steAsString) }
        val overflow = (frames?.size ?: 0) - 8
        if (overflow > 0) sb.append("\n\t... $overflow more")
        tp.cause?.let { cause ->
            sb.append("\nCaused by: ").append(cause.className)
            if (cause.message != null) sb.append(": ").append(cause.message)
            cause.stackTraceElementProxyArray?.take(4)?.forEach { sb.append("\n\tat ").append(it.steAsString) }
        }
        return sb.toString()
    }

    companion object {
        @Volatile private var console: ProxyConsole? = null

        fun attach(c: ProxyConsole) { console = c }
        fun detach() { console = null }

        private val TS_FMT = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss") }
    }
}
