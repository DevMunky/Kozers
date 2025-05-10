package dev.munky.kozers.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

fun <T, R> KSerializer<T>.xmap(serial: R.() -> T, deserial: T.() -> R): KSerializer<R> = object: KSerializer<R> {
    override val descriptor: SerialDescriptor = this@xmap.descriptor
    override fun deserialize(decoder: Decoder): R = this@xmap.deserialize(decoder).deserial()
    override fun serialize(encoder: Encoder, value: R) = this@xmap.serialize(encoder, value.serial())
}