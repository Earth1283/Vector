package dev.vector.proxy.console

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
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
        val style = when (event.level) {
            Level.ERROR -> theme.error
            Level.WARN -> theme.warn
            Level.INFO -> theme.info
            Level.DEBUG -> theme.debug
            else -> theme.trace
        }

        val ts = TS_FMT.get()!!.format(Date(event.timeStamp))
        val lvl = event.level.toString().padEnd(5)
        val log = event.loggerName.substringAfterLast('.')

        return AttributedStringBuilder()
            .style(AttributedStyle.DEFAULT.faint())
            .append("$ts ")
            .style(style.toAttributedStyle())
            .append(lvl)
            .style(AttributedStyle.DEFAULT.faint())
            .append(" $log ")
            .style(AttributedStyle.DEFAULT)
            .append(event.formattedMessage)
            .toAnsi()
    }

    companion object {
        @Volatile private var console: ProxyConsole? = null

        fun attach(c: ProxyConsole) { console = c }
        fun detach() { console = null }

        private val TS_FMT = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss") }
    }
}
