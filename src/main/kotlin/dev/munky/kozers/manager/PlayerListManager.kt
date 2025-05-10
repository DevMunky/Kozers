package dev.munky.kozers.manager

import com.mojang.authlib.GameProfile
import dev.munky.kozers.util.*
import io.papermc.paper.adventure.AdventureComponent
import kotlinx.coroutines.*
import me.lucko.spark.api.Spark
import me.lucko.spark.api.SparkProvider
import me.lucko.spark.api.statistic.StatisticWindow
import me.lucko.spark.paper.PaperSparkPlugin
import me.lucko.spark.paper.api.PaperSparkModule
import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftPlayer
import java.io.Closeable
import java.util.UUID

typealias TabListEntry = ClientboundPlayerInfoUpdatePacket.Entry
typealias TabListPacket = ClientboundPlayerInfoUpdatePacket
typealias TabListAction = ClientboundPlayerInfoUpdatePacket.Action

class PlayerListManager : Closeable {
    var isAlive = true
    private val scope = CoroutineScope(Dispatchers.Munky)

    private val loop = scope.launch {
        while (isAlive) {
            delay(1.ticks)
            val players = withContext(Dispatchers.Sync) {
                Bukkit.getOnlinePlayers()
            }
            for (player in players) {
                player.sendPlayerListHeader("<gray>Welcome to <yellow>Kozers</yellow>!".asComponent)
                player.sendPlayerListFooter("""
                    <blue>Ping: <white>${player.ping}ms
                    <green>TPS: ${SparkProvider.get().tps()?.poll(StatisticWindow.TicksPerSecond.SECONDS_10).toString().take(7)}
                    <yellow>MSPT: ${SparkProvider.get().mspt()?.poll(StatisticWindow.MillisPerTick.SECONDS_10)!!.mean().toString().take(7)}ms
                    <red>CPU: ${SparkProvider.get().cpuProcess().poll(StatisticWindow.CpuUsage.SECONDS_10).toString().take(7)}%
                    <green>RAM: ${(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()).toDouble() / Runtime.getRuntime().maxMemory()) * 100).toString().take(7)}%
                    """.trimIndent().asComponent)
            }
        }
    }

    private val maxLength = 20
    private val emptyGameProfile = GameProfile(
        UUID.randomUUID(),
        "whatever"
    ).apply {
        properties
    }

    private val updateLoop = scope.launch {
        return@launch
        while (isAlive) {
            delay(1.ticks)
            val players = withContext(Dispatchers.Sync) {
                ArrayList(Bukkit.getOnlinePlayers())
            }
            val cPlayers = players.map { (it as CraftPlayer).handle }
            for (player in cPlayers) {
                val packet = createPacket(player, cPlayers)
                player.connection.send(packet)
            }
        }
    }

    override fun close() {
        scope.cancel("Closed")
    }

    private fun createSpacerEntry(order: Int = 0, name: Component = "".asComponent): TabListEntry = TabListEntry(
        UUID.randomUUID(),
        GameProfile(
            UUID.randomUUID(), "whatever"
        ),
        true,
        0,
        GameType.SPECTATOR,
        AdventureComponent(name),
        true,
        order,
        null
    )

    private fun setColumnEntry(col: ArrayList<TabListEntry>, colOrder: Int, index: Int, name: Component) {
        col[index] = createSpacerEntry(colOrder + ((maxLength / 2) + index), name = name)
    }

    private fun createPacket(viewer: ServerPlayer, players: List<ServerPlayer>): TabListPacket {
        val serverInfoColumn = ArrayList<TabListEntry>()
        val contactInfoColumn = ArrayList<TabListEntry>()

        repeat(maxLength) {
            serverInfoColumn.add(createSpacerEntry())
            contactInfoColumn.add(createSpacerEntry())
        }

        setColumnEntry(serverInfoColumn, 80, 0, "TPS: ".asComponent)
        setColumnEntry(serverInfoColumn, 80, 1, "RAM %: ".asComponent)
        setColumnEntry(serverInfoColumn, 80, 2, "PING: ${viewer.connection.latency()}".asComponent)

        setColumnEntry(contactInfoColumn, 0, 0, "discord: ".asComponent)

        val playerList = ArrayList<TabListEntry>()
        repeat(maxLength * 2) {
            playerList.add(createSpacerEntry())
        }

        for ((i, player) in players.reversed().withIndex()) {
            playerList[i] = TabListEntry(
                player.uuid,
                player.gameProfile,
                true,
                player.connection.latency(),
                player.gameMode(),
                player.name,
                true,
                60 - i,
                player.chatSession?.asData()
            )
        }

        return TabListPacket(
            fullEnumSet(),
            serverInfoColumn + playerList + contactInfoColumn
        )
    }
}