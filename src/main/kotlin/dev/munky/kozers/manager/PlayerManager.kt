package dev.munky.kozers.manager

import dev.munky.kozers.KozersPlayer
import dev.munky.kozers.plugin
import dev.munky.kozers.serialization.Json
import dev.munky.kozers.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class PlayerManager: DataManager<KozersPlayer>("players/", true, ::Json), Closeable {
    override val serializer = KozersPlayer.serializer()
    private val players2kz = ConcurrentHashMap<UUID, KozersPlayer>()

    private val joinListener = listenTo<PlayerLoginEvent> {
        val id = it.player.uniqueId
        if (id in players2kz.keys) return@listenTo
        val kzPlayer = KozersPlayer(it.player, it.player.location)
        players2kz[id] = kzPlayer
        e2file[kzPlayer] = File(file, "${kzPlayer.uuid}.json")
        handleNewPlayer(kzPlayer)
    }

    private val quitListener = listenTo<PlayerQuitEvent> {
        var kz = players2kz[it.player.uniqueId]
        if (kz == null) {
            logger.error("Player '${it.player.name}' did not have associated kozers data.")
            return@listenTo
        }
        kz = kz.copy(lastKnownLocation = it.player.location)
        players2kz[it.player.uniqueId] = kz
    }

    fun handleNewPlayer(kp: KozersPlayer) {
        CoroutineScope(Dispatchers.Munky).launch {
            delay(1.seconds)
            Bukkit.getServer().broadcast(("Welcome ".asComponent + kp.player.displayName() + text("!")).color(NamedTextColor.GOLD))
        }
    }

    override fun onDecode(value: KozersPlayer) {
        logger.info("Read $value from file.")
        players2kz[value.uuid] = value
    }

    override fun close() {
        joinListener.unregister()
        quitListener.unregister()
    }

    operator fun get(player: Player): KozersPlayer = players2kz[player.uniqueId] ?: error("Player $player has no kozers player loaded.")

    companion object {
        private val logger = logger<PlayerManager>()
    }
}

fun Player.asKozersPlayer(): KozersPlayer = plugin.playerManager[this]