package dev.munky.kozers.util

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.config.Node
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.pattern.ConverterKeys
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)

@Plugin(name = "HeaderHighlighting", category = Node.CATEGORY)
@ConverterKeys("headerColor")
class HeaderColorConverter(name: String, style: String) : LogEventPatternConverter(name, style) {
    override fun format(event: LogEvent, toAppendTo: StringBuilder) {
        val level = event.level
        val color = when (level) {
            Level.ERROR -> "38;5;9"
            Level.WARN -> "38;2;255;215;0" // bright yellow
            Level.INFO -> "38;5;12" // bright cyan
            Level.DEBUG -> "38;2;5;210;165" // bright blue
            // I don't know
            Level.TRACE -> "0;37" // white
            else -> "0" // default
        }
        toAppendTo.append("\u001B[${color}m")
    }
    companion object {
        @PluginFactory
        @JvmStatic
        fun newInstance(options: Array<String>?): HeaderColorConverter {
            return HeaderColorConverter("headerColor", "style")
        }
    }
}

@Plugin(name = "MessageHighlighting", category = Node.CATEGORY)
@ConverterKeys("msgColor")
class MessageColorConverter(name: String, style: String) : LogEventPatternConverter(name, style) {
    override fun format(event: LogEvent, toAppendTo: StringBuilder) {
        val level = event.level
        val color = when (level) {
            Level.ERROR -> "48;2;180;20;20"
            Level.WARN -> "38;2;255;215;0" // bright yellow
            Level.INFO -> "38;2;5;210;165" // bright cyan\
            Level.DEBUG -> "38;2;20;120;220" // bright blue
            Level.TRACE -> "0;37" // white
            else -> "0" // default
        }
        toAppendTo.append("\u001B[${color}m")
    }
    companion object {
        @PluginFactory
        @JvmStatic
        fun newInstance(options: Array<String>?): MessageColorConverter {
            return MessageColorConverter("msgColor", "style")
        }
    }
}