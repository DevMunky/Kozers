package dev.munky.kozers.manager

import dev.munky.kozers.plugin
import dev.munky.kozers.serialization.Json
import dev.munky.kozers.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import org.bukkit.*
import org.bukkit.block.Biome
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.inventory.ItemStack
import java.io.Closeable
import java.lang.ref.SoftReference
import java.util.*
import kotlin.time.Duration.Companion.minutes

class WorldManager : DataManager<WorldManager.GroupData>("worlds/", true, ::Json), Closeable {
    override val serializer: KSerializer<GroupData> = GroupData.serializer()
    private val worlds = ArrayList<SoftReference<World>>()
    private val world2group = HashMap<UUID, GroupData>()

    private val worldChangeEvent = listenTo<PlayerChangedWorldEvent> { event ->
        val to = isMyWorld(event.player.world)
        val from = isMyWorld(event.from)
        if (from != null) handleFromMyWorld(from, event.player)
        if (to != null) handleToMyWorld(to, event.player)
    }

    private val joinEvent = listenTo<PlayerJoinEvent> {
        val to = isMyWorld(it.player.world)
        if (to != null) handleToMyWorld(to, it.player)
    }

    private val autoSave = CoroutineScope(Dispatchers.Munky).launch {
        while (isActive) {
            try {
                delay(10.minutes)
                for (e in world2group) {
                    val world = Bukkit.getWorld(e.key) ?: continue // weird, but ill ignore it
                    val players = withContext(Dispatchers.Sync) {
                        world.players
                    }
                    for (player in players) {
                        e.value.inventories[player.uniqueId] = player.inventory.contents
                    }
                }
                save()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                logger.error("Exception thrown while auto-saving player data in group data", t)
            }
        }
    }

    fun isMyWorld(world: World): World? {
        return worlds.firstOrNull { it.refersTo(world) }?.get()
    }

    fun handleFromMyWorld(from: World, player: Player) {
        logger.info("Heard player change worlds from one managed by me.")
        val group = world2group[from.uid] ?: error("World managed by me did not have an associated group ($from)")
        group.inventories[player.uniqueId] = player.inventory.contents
    }

    fun handleToMyWorld(world: World, player: Player) {
        logger.info("Heard player change worlds to one managed by me.")
        val group = world2group[world.uid] ?: error("World managed by me did not have an associated group ($world)")
        val inv = group.inventories[player.uniqueId] ?: emptyArray()
        player.inventory.contents = inv
    }

    val DEFAULT_WORLD_GROUP = WorldGroup(
        UUID.fromString("0a2e2cde-5b1d-4fa1-a068-81f3017eaa3c"),
        "default_group",
        listOf(
            WorldInfo(
                "",
                "world"
            ),
            WorldInfo(
                "",
                "world_the_end"
            ),
            WorldInfo(
                "",
                "world_nether"
            )
        )
    )

    fun start() {
        logger.info("Starting...")
        val config = plugin.configManager.get()
        val groups = config.worldGroups + DEFAULT_WORLD_GROUP
        for (group in groups) {
            logger.info("Loading world group '${group.name}'")
            for (info in group.worlds) {
                logger.info("Loading world '${info.name}'")
                try {
                    val gen = info.generator.let {
                        if (it == "" || it == "null" || it.startsWith("minecraft:")) null
                        else it
                    }
                    val env = when (info.generator) {
                        "minecraft:the_nether" -> World.Environment.NETHER
                        "minecraft:overworld" -> World.Environment.NORMAL
                        "minecraft:the_end" -> World.Environment.THE_END
                        else -> World.Environment.CUSTOM
                    }
                    val builder = WorldCreator
                        .name(info.name)
                        .environment(env)
                        .generator(gen)
                    val world = builder.createWorld()
                    if (world == null) {
                        logger.error("Could not create world")
                        logger.error("WorldCreator = $builder")
                        continue
                    } else {
                        var groupData = e2file.keys.firstOrNull { it.uuid == group.uuid }
                        if (groupData == null) {
                            groupData = GroupData(group.name, group.uuid, HashMap())
                            e2file[groupData] = file.resolve("${group.name}.json")
                        }
                        world2group[world.uid] = groupData
                        worlds += SoftReference(world)
                    }
                } catch (t: Throwable) {
                    logger.error("Exception thrown while creating or loading world ${info.name} in group ${group.name}.", t)
                }
            }
        }
    }

    private val reloadWorldBuilder = WorldCreator
        .ofKey(NamespacedKey("kozers", "reload_world"))
        .type(WorldType.FLAT)
        .biomeProvider(object: BiomeProvider() {
            override fun getBiome(worldInfo: org.bukkit.generator.WorldInfo, x: Int, y: Int, z: Int): Biome {
                return Biome.THE_VOID
            }
            override fun getBiomes(worldInfo: org.bukkit.generator.WorldInfo): MutableList<Biome> {
                return arrayListOf(Biome.THE_VOID)
            }
        })
    private var reloadWorld: World? = null

    override fun onDecode(value: GroupData) {
        logger.info("Loaded group data ${value.name}")
        super.onDecode(value)
    }

    fun sendPlayerToReloadWorld(player: Player) {
        var world = reloadWorld
        if (world == null) {
            world = reloadWorldBuilder.createWorld()
            if (world == null) {
                logger.error("Could not create reload world. '${player.name}' is on their own.")
                world = Bukkit.getWorlds().first()!!
            } else {
                world.isAutoSave = false
                reloadWorld = world
            }
        }
        player.teleport(world.spawnLocation)
    }

    fun closeReloadWorld() {
        val world = reloadWorld ?: return
        if (world.players.isNotEmpty()) {
            logger.warn("Players were still in the reload world upon its closure.")
            for (player in world.players) {
                player.teleport(Bukkit.getWorlds().first().spawnLocation)
            }
        }
        val unloaded = Bukkit.unloadWorld(world, false)
        if (!unloaded) logger.error("Could not unload reload world!")
        else logger.info("> Closed reload world as its no longer needed.")
        reloadWorld = null
    }

    override fun close() {
        logger.info("Shutting down...")
        autoSave.cancel("Shutting down")
        val reloadPlayers = HashMap<Player, World>()
        for (world in worlds) {
            val referent = world.get()
            if (referent == null) {
                logger.warn("World already reclaimed by garbage collector. Safe to assume its been unloaded properly.")
                continue
            }
            val players = referent.players
            if (players.isNotEmpty()) {
                referent.players.forEach {
                    sendPlayerToReloadWorld(it)
                    reloadPlayers[it] = referent
                }
                logger.info("> Waiting to remove all players from world '${referent.name}'...")
                MinecraftServer.getServer().doRunTask(TickTask(MinecraftServer.currentTick + 30) {
                    // cant unload overworld...
                    val unloaded = Bukkit.unloadWorld(referent, true)
                    if (unloaded) logger.info("> Unloaded '${referent.name}' after waiting 20 ticks for players to move out.")
                    else {
                        logger.error("Could not unload world '${referent.name}'")
                    }
                })
            } else {
                val unloaded = Bukkit.unloadWorld(referent, true)
                if (unloaded) logger.info("> Unloaded '${referent.name}'.")
                else {
                    logger.warn("Could not unload world '${referent.name}'")
                }
            }
        }
        if (reloadPlayers.isNotEmpty()) {
            for (e in reloadPlayers) {
                e.key.teleport(e.value.spawnLocation)
            }
            closeReloadWorld()
        }
        joinEvent.unregister()
        worldChangeEvent.unregister()
    }

    @Serializable
    data class GroupData(
        val name: String,
        val uuid: @Contextual UUID,
        val inventories: MutableMap<@Contextual UUID, Array<out @Contextual ItemStack?>>,
    )

    companion object {
        private val logger = logger<WorldManager>()
    }
}