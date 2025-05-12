package dev.munky.kozers.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

val TENEBRIS_COLOR = TextColor.color(35, 80, 70)

fun text(content: String): Component = Component.text(content)

fun Component.tenebrisColor(): Component = color(TENEBRIS_COLOR)

val DEFAULT_MINIMESSAGE = MiniMessage.builder()
    .editTags { tagBuilder ->
        tagBuilder.tag(
            "tenebris_color",
            Tag.styling {
                it.apply(TENEBRIS_COLOR)
            }
        )
    }
    .build()

operator fun Component.plus(other: Component): Component = append(other)


fun miniText(content: String) = DEFAULT_MINIMESSAGE.deserialize(content)

val String.asComponent: Component get() = miniText(this)

val Component.asString: String get() = PlainTextComponentSerializer.plainText().serialize(this)

val Component.asMini: String get() = DEFAULT_MINIMESSAGE.serialize(this)

// I made this really quick im so smart
fun String.toSnakeCase(): String {
    var str = ""
    var previous = '\u0000'
    for (c in this) {
        str += when {
            c.isUpperCase() && !previous.isLetter() -> c.lowercase()
            c.isUpperCase() -> "_${c.lowercase()}"
            c.isWhitespace() -> '_'
            else -> c
        }
        previous = c
    }
    return str
}

fun String.toSentenceCase(): String {
    var str = ""
    var previous = '\u0000'
    for (c in this) {
        str += when {
            c.isLowerCase() && !previous.isLetter() -> c.uppercase()
            c in arrayOf('-', '.', '_') -> ' '
            c.isUpperCase() && previous.isLetter() -> " $c"
            else -> c
        }
        previous = c
    }
    return str
}