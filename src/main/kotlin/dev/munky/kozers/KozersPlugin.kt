package dev.munky.kozers

import dev.munky.kozers.command.KItemCommand
import dev.munky.kozers.command.ReloadCommand
import dev.munky.kozers.command.WorldCommand
import dev.munky.kozers.manager.*
import dev.munky.kozers.serialization.Json
import dev.munky.kozers.serialization.Toml
import dev.munky.kozers.util.logger
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class KozersPlugin : JavaPlugin() {
    init {
        plugin = this
    }

    override fun getLogger(): Logger = object: Logger("Kozers", null) {
        override fun info(msg: String?) = LOGGER.info(msg)
        override fun warning(msg: String?) = LOGGER.warn(msg)
        override fun severe(msg: String?) = LOGGER.error(msg)
    }

    private var initializing = false

    var playerManager: PlayerManager by ManagerDelegate()
        private set
    var chatManager: ChatManager by ManagerDelegate()
        private set
    var playerListManager: PlayerListManager by ManagerDelegate()
        private set
    var configManager: ConfigManager by ManagerDelegate()
        private set
    var worldManager: WorldManager by ManagerDelegate()
        private set

    private class ManagerDelegate<T: Any> : ReadWriteProperty<KozersPlugin, T> {
        lateinit var manager: T

        override fun getValue(thisRef: KozersPlugin, property: KProperty<*>): T {
            when {
                this::manager.isInitialized &&
                        !thisRef.isEnabled &&
                        !thisRef.initializing
                -> error("Kozers is disabled, cannot access ${property.name}.")
                !this::manager.isInitialized && thisRef.isEnabled -> error("Kozers is enabled, yet ${property.name} was not initialized.")
            }
            return manager
        }

        override fun setValue(thisRef: KozersPlugin, property: KProperty<*>, value: T) {
            manager = value
        }
    }

    fun reload() = runBlocking {
        playerManager.save()
        playerManager.close()
        chatManager.close()
        playerListManager.close()
        worldManager.save()
        worldManager.close()

        configManager = ConfigManager()
        configManager.load()
        playerManager = PlayerManager()
        playerManager.load()
        worldManager = WorldManager()
        worldManager.load()

        chatManager = ChatManager()
        playerListManager = PlayerListManager()

        worldManager.start()
    }

    override fun onLoad() {
        initializing = true
        configManager = ConfigManager()
        runBlocking {
            configManager.load()
        }
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(KItemCommand.build())
            it.registrar().register(WorldCommand.build())
            it.registrar().register(Commands
                .literal("kozers")
                .then(ReloadCommand.build())
                .build()
            )
        }
        initializing = false
        LOGGER.info("Loaded")
    }

    override fun onEnable() {
        initializing = true
        playerManager = PlayerManager()
        chatManager = ChatManager()
        playerListManager = PlayerListManager()
        worldManager = WorldManager()
        runBlocking {
            playerManager.load()
            worldManager.load()
        }
        worldManager.start()
        initializing = false

        LOGGER.info("Enabled")
    }

    override fun onDisable() {
        initializing = true
        playerManager.close()
        runBlocking {
            playerManager.save()
            worldManager.save()
        }
        worldManager.close()
        initializing = false
        LOGGER.info("Disabled")
    }

    companion object {
        private val LOGGER = logger<KozersPlugin>()
    }
}

lateinit var plugin: KozersPlugin
    private set