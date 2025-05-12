package dev.munky.kozers.util

import dev.munky.kozers.plugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import net.minecraft.server.MinecraftServer
import org.bukkit.Bukkit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * I feel context switching is so much better than creating new sync contexts.
 */
val Dispatchers.Munky get() = MunkyDispatcher
val Dispatchers.Sync get() = SyncDispatcher

object MunkyDispatcher: CoroutineDispatcher() {
    private val munkyGroup = ThreadGroup("MunkyDispatchers")
    private val index = AtomicInteger(0)
    private val logger = logger<MunkyDispatcher>()

    private val dispatcher = Executors
        .newCachedThreadPool {
            Thread
                .ofPlatform()
                .group(munkyGroup)
                .daemon()
                .name("munky-dispatcher-${index.getAndIncrement()}")
                .uncaughtExceptionHandler { t, e ->
                    logger.error("Uncaught exception in dispatcher", e)
                }
                .unstarted(it)
        }
        .asCoroutineDispatcher()

    override fun dispatch(context: CoroutineContext, block: Runnable) = dispatcher.dispatch(context, block)
}

/**
 * For context switching to the main thread.
 *
 * For some reason minecraft stops scheduling when the server is shutting down
 * and just ___runs tasks on the caller thread___.
 */
object SyncDispatcher: CoroutineDispatcher() {
    private val logger = logger<SyncDispatcher>()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Bukkit.isPrimaryThread()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!isDispatchNeeded(context)) Dispatchers.Unconfined.dispatch(context, block)
        else MinecraftServer.getServer().execute(block)
    }
}