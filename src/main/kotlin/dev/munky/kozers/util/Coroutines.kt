package dev.munky.kozers.util

import io.papermc.paper.util.Tick
import kotlinx.coroutines.delay
import net.minecraft.server.MinecraftServer
import kotlin.time.toKotlinDuration

inline val Number.ticks get() = Tick.of(toLong()).toKotlinDuration()

suspend fun delayTicks(ticks: Long) = delay(
    ((20f / MinecraftServer.getServer().tickRateManager().tickrate())
            * 1000f
            * (ticks / 20f))
        .toLong()
)