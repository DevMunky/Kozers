package dev.munky.kozers.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.EnumSet
import kotlin.reflect.KClass

inline fun <reified T : Enum<T>> emptyEnumSet(): EnumSet<T> {
    return EnumSet.noneOf(T::class.java)
}

inline fun <reified T : Enum<T>> fullEnumSet(): EnumSet<T> {
    return EnumSet.allOf(T::class.java)
}

inline fun <reified T : Enum<T>> enumSetOf(vararg es: T): EnumSet<T> =
    emptyEnumSet<T>().apply {
        if (isNotEmpty()) for (e in es) add(e)
    }

/**
 * Creates an enum set filled with all enums that satisfied the predicate.
 */
inline fun <reified T: Enum<T>> enumSetOf(predicate: (T) -> Boolean): EnumSet<T> = emptyEnumSet<T>().apply { for (e in enumValues<T>()) if (predicate(e)) add(e) }

inline fun <reified T : Enum<T>> EnumSetSerializer(): KSerializer<EnumSet<T>> = EnumSetSerializer(T::class)

@PublishedApi
internal class EnumSetSerializer<T : Enum<T>>(val universe: KClass<T>): KSerializer<EnumSet<T>> {
    val serializer = ListSerializer(EnumSerializer(universe))

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): EnumSet<T> {
        val list = serializer.deserialize(decoder)
        return if (list.isEmpty()) EnumSet.noneOf(universe.java) else EnumSet.copyOf(list)
    }

    override fun serialize(encoder: Encoder, value: EnumSet<T>) {
        serializer.serialize(encoder, value.toList())
    }
}

inline fun <reified T : Enum<T>> EnumSerializer(): KSerializer<T> = EnumSerializer(T::class)

@PublishedApi
internal class EnumSerializer<T: Enum<T>>(val universe: KClass<T>): KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("enum", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val types = universe.java.enumConstants
        val name = decoder.decodeString()
        return types.firstOrNull { it.name == name } ?: error("No enum constant in ${universe.qualifiedName} named $name")
    }

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)
}