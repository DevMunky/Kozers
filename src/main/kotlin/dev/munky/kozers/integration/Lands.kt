package dev.munky.kozers.integration

import dev.munky.kozers.plugin
import me.angeschossen.lands.api.LandsIntegration
import org.bukkit.Bukkit

object Lands: LandsIntegration by LandsIntegration.of(plugin) {
    init {

    }
}