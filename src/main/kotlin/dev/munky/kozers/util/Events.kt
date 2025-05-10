package dev.munky.kozers.util

import dev.munky.kozers.plugin
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.slf4j.LoggerFactory
import java.util.function.BiConsumer

private val logger = LoggerFactory.getLogger("dev.munky.kozers.util.Events")

fun <T : Event> listenTo(
    eventClass: Class<T>,
    priority: EventPriority,
    eventAndListener: BiConsumer<T, Listener>
): Listener {
    val listener: Listener = object : Listener {}
    val executor = EventExecutor { _: Listener, e: Event ->
        try {
            if (eventClass.isInstance(e)) eventAndListener.accept(eventClass.cast(e), listener)
        } catch (t: Throwable) {
            logger.error("An event executor registered through TitanCore's ListenerFactory threw an exception", t)
        }
    }
    Bukkit.getPluginManager().registerEvent(eventClass, listener, priority, executor, plugin)
    return listener
}

inline fun <reified T: Event> listenTo(
    noinline f: (T) -> Unit
): Listener = listenTo(T::class.java, EventPriority.NORMAL) { t, _ -> f(t) }

inline fun <reified T: Event> listenTo(
    priority: EventPriority,
    noinline f: (T) -> Unit
): Listener = listenTo(T::class.java, priority) { t, _ -> f(t) }

inline fun <reified T: Event> listenTo(
    priority: EventPriority,
    noinline f: (T, Listener) -> Unit
): Listener = listenTo(T::class.java, priority, f)

fun Listener.unregister() {
    HandlerList.unregisterAll(this)
}