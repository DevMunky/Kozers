package dev.munky.kozers.util

import io.papermc.paper.util.Tick
import kotlin.time.toKotlinDuration

inline val Number.ticks get() = Tick.of(toLong()).toKotlinDuration()