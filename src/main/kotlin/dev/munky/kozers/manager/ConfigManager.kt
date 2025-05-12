package dev.munky.kozers.manager

import dev.munky.kozers.serialization.Json
import dev.munky.kozers.serialization.Toml
import dev.munky.kozers.util.logger
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.*

class ConfigManager : DataManager<TheConfig>("config.json", false, { Json }) {
    override val serializer: KSerializer<TheConfig> = TheConfig.serializer()

    private var configDecoded = false
    override fun onDecode(value: TheConfig) {
        if (configDecoded) error("No more than one config.json allowed.")
        configDecoded = true
        logger.info("Loaded config")
    }

    fun get(): TheConfig = e2file.keys.firstOrNull() ?: TheConfig.default().also { e2file.keys.add(it) }

    companion object {
        private val logger = logger<TheConfig>()
    }
}

@Serializable
data class TheConfig(
    val worldGroups: List<WorldGroup>
) {
    companion object {
        fun default() = TheConfig(emptyList())
    }
}

@Serializable
data class WorldGroup(
    val uuid: @Contextual UUID,
    val name: String,
    val worlds: List<WorldInfo>
)

@Serializable
data class WorldInfo(
    val generator: String,
    val name: String
)