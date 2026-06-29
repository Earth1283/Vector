package dev.vector.proxy.console

import org.jline.utils.AttributedStyle

private val BRIGHT_GREEN  = AttributedStyle.BRIGHT or AttributedStyle.GREEN
private val BRIGHT_YELLOW = AttributedStyle.BRIGHT or AttributedStyle.YELLOW
private val BRIGHT_RED    = AttributedStyle.BRIGHT or AttributedStyle.RED
private val BRIGHT_CYAN   = AttributedStyle.BRIGHT or AttributedStyle.CYAN

data class ThemeStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val faint: Boolean = false,
    val foreground: Int = -1,
    val colorMessage: Boolean = false,
) {
    fun toAttributedStyle(): AttributedStyle {
        var s = AttributedStyle.DEFAULT
        if (bold) s = s.bold()
        if (italic) s = s.italic()
        if (faint) s = s.faint()
        if (foreground >= 0) s = s.foreground(foreground)
        return s
    }

    /** Color only, no bold/italic/faint — for coloring message text. */
    fun toMessageStyle(): AttributedStyle {
        var s = AttributedStyle.DEFAULT
        if (foreground >= 0) s = s.foreground(foreground)
        return s
    }
}

class ThemeStyleBuilder {
    var bold: Boolean = false
    var italic: Boolean = false
    var faint: Boolean = false
    var colorMessage: Boolean = false
    private var foreground: Int = -1

    fun color(ansiColor: Int) { foreground = ansiColor }
    fun build() = ThemeStyle(bold, italic, faint, foreground, colorMessage)
}

class ConsoleTheme(
    val info:  ThemeStyle = ThemeStyle(foreground = BRIGHT_GREEN),
    val warn:  ThemeStyle = ThemeStyle(bold = true, foreground = BRIGHT_YELLOW, colorMessage = true),
    val error: ThemeStyle = ThemeStyle(bold = true, foreground = BRIGHT_RED,    colorMessage = true),
    val debug: ThemeStyle = ThemeStyle(foreground = BRIGHT_CYAN),
    val trace: ThemeStyle = ThemeStyle(faint = true),
)

class ConsoleThemeBuilder {
    private var info:  ThemeStyle = ThemeStyle(foreground = BRIGHT_GREEN)
    private var warn:  ThemeStyle = ThemeStyle(bold = true, foreground = BRIGHT_YELLOW, colorMessage = true)
    private var error: ThemeStyle = ThemeStyle(bold = true, foreground = BRIGHT_RED,    colorMessage = true)
    private var debug: ThemeStyle = ThemeStyle(foreground = BRIGHT_CYAN)
    private var trace: ThemeStyle = ThemeStyle(faint = true)

    fun info(block: ThemeStyleBuilder.() -> Unit)  { info  = ThemeStyleBuilder().apply(block).build() }
    fun warn(block: ThemeStyleBuilder.() -> Unit)  { warn  = ThemeStyleBuilder().apply(block).build() }
    fun error(block: ThemeStyleBuilder.() -> Unit) { error = ThemeStyleBuilder().apply(block).build() }
    fun debug(block: ThemeStyleBuilder.() -> Unit) { debug = ThemeStyleBuilder().apply(block).build() }
    fun trace(block: ThemeStyleBuilder.() -> Unit) { trace = ThemeStyleBuilder().apply(block).build() }

    fun build() = ConsoleTheme(info, warn, error, debug, trace)
}

fun theme(block: ConsoleThemeBuilder.() -> Unit): ConsoleTheme =
    ConsoleThemeBuilder().apply(block).build()

fun defaultTheme(): ConsoleTheme = ConsoleTheme()
