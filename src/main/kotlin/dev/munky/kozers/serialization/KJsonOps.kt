package dev.munky.kozers.serialization

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.util.ArrayList
import java.util.stream.Collectors
import java.util.stream.Stream

object KJsonOps : DynamicOps<JsonElement> {
    override fun empty(): JsonElement {
        return JsonNull
    }

    override fun <U : Any?> convertTo(outOps: DynamicOps<U>, input: JsonElement): U {
        return when(input){
            is JsonObject -> convertMap(outOps, input)
            is JsonArray -> convertList(outOps, input)
            JsonNull -> outOps.empty()
            is JsonPrimitive -> {
                val primitive = input.jsonPrimitive
                when {
                    primitive.isString -> outOps.createString(primitive.content)
                    primitive.booleanOrNull != null -> outOps.createBoolean(primitive.boolean)
                    else -> {
                        val value = BigDecimal(primitive.double)
                        try {
                            when (val l = value.longValueExact()) {
                                0L -> outOps.createBoolean(false)
                                1L -> outOps.createBoolean(true)
                                l.toByte().toLong() -> outOps.createByte(l.toByte())
                                l.toShort().toLong() -> outOps.createShort(l.toShort())
                                l.toInt().toLong() -> outOps.createInt(l.toInt())
                                else -> outOps.createLong(l)
                            }
                        } catch (e: ArithmeticException) {
                            when(val d = value.toDouble()) {
                                d.toFloat().toDouble() -> outOps.createFloat(d.toFloat())
                                else -> outOps.createDouble(d)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun createNumeric(i: Number?): JsonElement = JsonPrimitive(i)

    override fun createString(value: String?): JsonElement = JsonPrimitive(value)

    override fun remove(input: JsonElement?, key: String?): JsonElement? {
        return when (input) {
            is JsonObject -> {
                buildJsonObject {
                    for (e in input) {
                        if (e.key == key) continue
                        put(e.key, e.value)
                    }
                }
            }
            else -> input
        }
    }

    override fun createList(input: Stream<JsonElement>): JsonElement = JsonArray(input.collect(Collectors.toList()))

    override fun getStream(input: JsonElement?): DataResult<Stream<JsonElement>> {
        return when (input) {
            is JsonArray -> {
                DataResult.success(input.stream())
            }
            is JsonObject -> {
                val list = ArrayList<JsonElement>()
                for (e in input) {
                    list += JsonPrimitive(e.key)
                    list += e.value
                }
                DataResult.success(list.stream())
            }
            else -> DataResult.error { "Not a json array" }
        }
    }

    override fun createMap(map: Stream<Pair<JsonElement, JsonElement>>): JsonElement {
        return buildJsonObject {
            map.forEach {
                put(it.first.jsonPrimitive.content, it.second)
            }
        }
    }

    override fun getMapValues(input: JsonElement?): DataResult<Stream<Pair<JsonElement, JsonElement>>> {
        if (input == empty()) return DataResult.success(Stream.empty())
        if (input !is JsonObject) return DataResult.error { "getMapValues called with '${input}'" }
        val list = ArrayList<Pair<JsonElement, JsonElement>>()
        for (e in input) {
            list.add(Pair.of(JsonPrimitive(e.key), e.value))
        }
        return DataResult.success(list.stream())
    }

    override fun mergeToMap(map: JsonElement?, key: JsonElement, value: JsonElement): DataResult<JsonElement> {
        if (key !is JsonPrimitive) return DataResult.error { "Key must be primitive" }
        return when (map) {
            empty() -> DataResult.success(buildJsonObject {
                put(key.content, value)
            })
            is JsonObject -> {
                val obj = buildJsonObject {
                    for (e in map) {
                        put(e.key, e.value)
                    }
                    put(key.jsonPrimitive.content, value)
                }
                DataResult.success(obj)
            }
            else -> DataResult.error { "mergeToMap called with not a map: $map" }
        }
    }

    override fun mergeToList(list: JsonElement?, value: JsonElement): DataResult<JsonElement> {
        if (list !is JsonArray && list != empty()) return DataResult.error { "mergeToList called with not a list: $list" }
        return DataResult.success(buildJsonArray {
            if (list is JsonArray) for (e in list) add(e)
            add(value)
        })
    }

    override fun getStringValue(input: JsonElement?): DataResult<String> {
        return when (input) {
            is JsonPrimitive -> DataResult.success(input.content)
            else -> DataResult.error { "Not a primitive" }
        }
    }

    override fun getNumberValue(input: JsonElement?): DataResult<Number> {
        return when (input) {
            is JsonPrimitive -> DataResult.success(input.doubleOrNull)
            else -> DataResult.error { "Not a primitive" }
        }
    }
}