package dev.vector.proxy.console

import org.jline.utils.AttributedStyle

data class ThemeStyle(
    val bold: Boolean = false,
    val faint: Boolean = false,
    val foreground: Int = -1,
) {
    fun toAttributedStyle(): AttributedStyle {
        var s = AttributedStyle.DEFAULT
        if (bold) s = s.bold()
        if (faint) s = s.faint()
        if (foreground >= 0) s = s.foreground(foreground)
        return s
    }
}

class ThemeStyleBuilder {
    var bold: Boolean = false
    var faint: Boolean = false
    private var foreground: Int = -1

    fun color(ansiColor: Int) { foreground = ansiColor }
    fun build() = ThemeStyle(bold, faint, foreground)
}

class ConsoleTheme(
    val info: ThemeStyle = ThemeStyle(foreground = AttributedStyle.GREEN),
    val warn: ThemeStyle = ThemeStyle(bold = true, foreground = AttributedStyle.YELLOW),
    val error: ThemeStyle = ThemeStyle(bold = true, foreground = AttributedStyle.RED),
    val debug: ThemeStyle = ThemeStyle(faint = true),
    val trace: ThemeStyle = ThemeStyle(faint = true),
)

class ConsoleThemeBuilder {
    private var info: ThemeStyle = ThemeStyle(foreground = AttributedStyle.GREEN)
    private var warn: ThemeStyle = ThemeStyle(bold = true, foreground = AttributedStyle.YELLOW)
    private var error: ThemeStyle = ThemeStyle(bold = true, foreground = AttributedStyle.RED)
    private var debug: ThemeStyle = ThemeStyle(faint = true)
    private var trace: ThemeStyle = ThemeStyle(faint = true)

    fun info(block: ThemeStyleBuilder.() -> Unit) { info = ThemeStyleBuilder().apply(block).build() }
    fun warn(block: ThemeStyleBuilder.() -> Unit) { warn = ThemeStyleBuilder().apply(block).build() }
    fun error(block: ThemeStyleBuilder.() -> Unit) { error = ThemeStyleBuilder().apply(block).build() }
    fun debug(block: ThemeStyleBuilder.() -> Unit) { debug = ThemeStyleBuilder().apply(block).build() }
    fun trace(block: ThemeStyleBuilder.() -> Unit) { trace = ThemeStyleBuilder().apply(block).build() }

    fun build() = ConsoleTheme(info, warn, error, debug, trace)
}

fun theme(block: ConsoleThemeBuilder.() -> Unit): ConsoleTheme =
    ConsoleThemeBuilder().apply(block).build()

fun defaultTheme(): ConsoleTheme = ConsoleTheme()
