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

    override fun onDecode(value: TheConfig) {
        logger.info("Loaded config")
    }

    fun get(): TheConfig = e2file.keys.first()

    companion object {
        private val logger = logger<TheConfig>()
    }
}

@Serializable
data class TheConfig(
    val worldGroups: List<WorldGroup>
)

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