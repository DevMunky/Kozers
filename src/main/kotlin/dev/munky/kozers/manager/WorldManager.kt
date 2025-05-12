package dev.munky.kozers.manager

import dev.munky.kozers.plugin
import dev.munky.kozers.serialization.Json
import dev.munky.kozers.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import net.kyori.adventure.util.TriState
import org.bukkit.*
import org.bukkit.block.Biome
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.generator.BiomeProvider
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
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

    // Reading inventory data and writing inventory data through the server is quite hard.
    // This way, everything is in-house. This is also kind of more flexible so im not unhappy with it.
    val DEFAULT_WORLD_GROUP = WorldGroup(
        UUID.fromString("0a2e2cde-5b1d-4fa1-a068-81f3017eaa3c"),
        "default_group",
        listOf(
            WorldInfo(
                "minecraft:overworld",
                "world"
            ),
            WorldInfo(
                "minecraft:the_end",
                "world_the_end"
            ),
            WorldInfo(
                "minecraft:the_nether",
                "world_nether"
            )
        )
    )

    fun start() = CoroutineScope(Dispatchers.Munky).launch {
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
                        "minecraft:the_end" -> World.Environment.THE_END
                        // "minecraft:overworld",
                        else -> World.Environment.NORMAL
                    }
                    val builder = WorldCreator
                        .name(info.name)
                        .environment(env)
                        .generator(gen)
                    val world = withContext(Dispatchers.Sync) { builder.createWorld() }
                    if (world == null) {
                        logger.error("Could not create world")
                        logger.error("WorldCreator = $builder")
                        continue
                    } else {
                        // Find existing group data or make one to put in persistent map for saving
                        var groupData = e2file.keys.firstOrNull { it.uuid == group.uuid }
                        if (groupData == null) {
                            groupData = GroupData(group.name, group.uuid, HashMap())
                            e2file[groupData] = file.resolve("${group.name}.json")
                        }
                        world2group[world.uid] = groupData
                        // only store world by soft reference
                        worlds += SoftReference(world)
                    }
                } catch (t: Throwable) {
                    logger.error("Exception thrown while creating or loading world ${info.name} in group ${group.name}.", t)
                }
            }
        }
        if (reloadPlayers.isNotEmpty()) {
            logger.info("Moving players back into their worlds")
            if (reloadWorld != null) for (e in reloadPlayers) {
                val player = e.key
                val (worldName, vector, yaw, pitch) = e.value
                val world = Bukkit.getWorld(worldName)
                if (world == null) {
                    logger.warn("Player's previous world no longer exists")
                    withContext(Dispatchers.Sync) {
                        player.teleport(Bukkit.getWorlds().first().spawnLocation)
                    }
                    continue
                }
                withContext(Dispatchers.Sync) {
                    logger.info("Sent '${player.name}' to their previous position.")
                    player.teleport(vector.toLocation(world, yaw, pitch))
                }
            }
            closeReloadWorld()
        }
        logger.info("Started")
    }

    override fun onDecode(value: GroupData) {
        logger.info("Loaded group data ${value.name}")
        super.onDecode(value)
    }

    suspend fun sendPlayerToReloadWorld(player: Player) = coroutineScope {
        reloadPlayers[player] = LocationWithUnsafeWorld(player.world.name, player.location.toVector(), player.yaw, player.pitch)
        val world = reloadWorld
            ?: reloadWorldBuilder.createWorld()?.also {
                it.isAutoSave = false
                reloadWorld = it
                logger.info("Created reload world.")
            }
            ?: run {
                logger.error("Could not create reload world. '${player.name}' is on their own.")
                Bukkit.getWorlds().first()!!
            }
        withContext(Dispatchers.Sync) {
            logger.info("Sent '${player.name}' to reload world.")
            player.teleport(world.spawnLocation)
        }
    }

    suspend fun closeReloadWorld() = coroutineScope {
        val rWorld = reloadWorld ?: return@coroutineScope
        logger.info("Closing reload world...")
        if (rWorld.players.isNotEmpty()) {
            logger.warn("Players were still in the reload world upon its closure.")
        }
        val unloaded = withContext(Dispatchers.Sync) {
            Bukkit.unloadWorld(rWorld, false)
        }
        if (!unloaded) logger.error("Could not unload reload world!")
        else logger.info("> Closed reload world as its no longer needed.")
        reloadWorld = null
        reloadPlayers.clear()
    }

    override fun close() = runBlocking {
        logger.info("Shutting down...")
        autoSave.cancel("Shutting down")
        for (world in worlds) {
            val referent = world.get()
            if (referent == null) {
                logger.warn("World already reclaimed by garbage collector. Safe to assume its been unloaded properly.")
                continue
            }
            logger.info("> Trying to unload world '${referent.name}'.")
            val unloaded = withContext(Dispatchers.Sync) {
                val players = referent.players
                for (p in players) {
                    sendPlayerToReloadWorld(p)
                }
                Bukkit.unloadWorld(referent, true)
            }
            if (unloaded) logger.info("> Unloaded '${referent.name}'.")
            else {
                logger.warn("Could not unload world '${referent.name}'")
            }
        }
        joinEvent.unregister()
        worldChangeEvent.unregister()
        logger.info("Shutdown")
    }

    @Serializable
    data class GroupData(
        val name: String,
        val uuid: @Contextual UUID,
        val inventories: MutableMap<@Contextual UUID, Array<out @Contextual ItemStack?>>,
    )

    private data class LocationWithUnsafeWorld(
        val world: String, // name of world, uuid changes between reloads.
        val vector: Vector,
        val yaw: Float,
        val pitch: Float
    )

    companion object {
        private val logger = logger<WorldManager>()

        private val reloadWorldBuilder = WorldCreator
            .ofKey(NamespacedKey("kozers", "reload_world"))
            .type(WorldType.FLAT)
            .keepSpawnLoaded(TriState.FALSE)
            .generateStructures(false)
            .biomeProvider(object: BiomeProvider() {
                override fun getBiome(worldInfo: org.bukkit.generator.WorldInfo, x: Int, y: Int, z: Int): Biome {
                    return Biome.THE_VOID
                }
                override fun getBiomes(worldInfo: org.bukkit.generator.WorldInfo): MutableList<Biome> {
                    return arrayListOf(Biome.THE_VOID)
                }
            })

        // Strong reference because I own this world.
        private var reloadWorld: World? = null

        // player -> world.uid
        private val reloadPlayers = HashMap<Player, LocationWithUnsafeWorld>()
    }
}