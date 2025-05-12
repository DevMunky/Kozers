package dev.munky.kozers.item

import dev.munky.kozers.util.Munky
import dev.munky.kozers.util.asComponent
import dev.munky.kozers.util.delayTicks
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.datacomponent.item.UseCooldown
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.keys.SoundEventKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.server.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object Ambrosia : KItem() {
    const val MAX_USES = 30

    override val name: (ItemStack) -> Component = { "<gold>Ambrosia".asComponent }

    override val description: (ItemStack) -> List<Component> = {
        val usesRemaining = getPersistentData(it) { getInt("uses_remaining") }.orElse(MAX_USES)
        listOf(
            "<gray>A magical food.".asComponent,
            "<gray>Buffs you upon consumption.".asComponent,
            "<gray>It has $usesRemaining uses remaining.".asComponent
        )
    }

    const val COOLDOWN_SECONDS = 30f

    override fun transform0(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown
            .useCooldown(COOLDOWN_SECONDS)
            .cooldownGroup(Key.key("kozers", "ambrosia"))
        )
        item.setData(DataComponentTypes.ITEM_MODEL, ItemType.WHEAT.key())
        item.setData(DataComponentTypes.MAX_DAMAGE, MAX_USES)
        val uses = getPersistentData(item) {
            getInt("uses_remaining")
        }.orElse(MAX_USES)
        item.setData(DataComponentTypes.DAMAGE, MAX_USES - uses)
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.DAMAGE).build())
        return item
    }

    init {
        handle(PlayerInteractEvent::class) {
            val item = it.item ?: return@handle
            if (item.kItem !is Ambrosia) return@handle
            if (!it.player.isCooldownComplete(item)) {
                return@handle
            }
            val usesRemaining = getPersistentData(item) { getInt("uses_remaining") }.orElse(MAX_USES) - 1
            editPersistentData(item) {
                putInt("uses_remaining", usesRemaining)
            }
            transform(item)
            if (usesRemaining < 1) {
                it.player.playSound(SoundEventKeys.ENTITY_WARDEN_HEARTBEAT, 1f, 2f)
                it.player.inventory.removeItem(item)
            }
            it.player.playSound(SoundEventKeys.ENTITY_WARDEN_STEP, 1f, 1.5f)
            it.player.glow(NamedTextColor.GOLD)
            it.player.addPotionEffect(PotionEffect(
                PotionEffectType.HASTE,
                COOLDOWN_SECONDS.toInt() * 20,
                1,
                true,
                false,
                true
            ))
            CoroutineScope(Dispatchers.Munky).launch {
                delayTicks(COOLDOWN_SECONDS.toLong() * 20L)
                it.player.stopGlow()
            }
            it.player.sendActionBar("<gold>Ate some ambrosia...".asComponent)
        }
        initialize()
    }
}

fun Player.playSound(sound: TypedKey<Sound>, vol: Float = 1f, pitch: Float = 1f) = playSound(location, Registry.SOUND_EVENT.get(sound)!!, vol, pitch)
fun World.playSound(sound: TypedKey<Sound>, at: Location, vol: Float = 1f, pitch: Float = 1f) = playSound(at, Registry.SOUND_EVENT.get(sound)!!, vol, pitch)

fun Player.glow(color: NamedTextColor) {
    val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    val team = scoreboard.getTeam("glowColorOverride-${color}")
        ?: scoreboard.registerNewTeam("glowColorOverride-${color}")
            .apply { color(color) }
    team.addPlayer(this)
    MinecraftServer.getServer().execute {
        addPotionEffect(PotionEffect(
            PotionEffectType.GLOWING,
            -1,
            255,
            true,
            false,
            true
        ))
    }
}

fun Player.stopGlow() {
    val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    scoreboard.getPlayerTeam(this)?.removePlayer(this)
    MinecraftServer.getServer().execute { removePotionEffect(PotionEffectType.GLOWING) }
}