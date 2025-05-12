package dev.munky.kozers.item

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.kozers.util.Munky
import dev.munky.kozers.util.Sync
import dev.munky.kozers.util.asComponent
import dev.munky.kozers.util.delayTicks
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Equippable
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.registry.keys.SoundEventKeys
import kotlinx.coroutines.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.block.BlockType
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import java.text.NumberFormat

object CrimsonCrown : KItem() {
    override val name: (ItemStack) -> Component = {
        "<red><bold>The Crimson Crown".asComponent
    }

    override val description: (ItemStack) -> List<Component> = {
        val totalBleedDamage = getPersistentData(it) {
            getDouble("total_bleed_damage")
        }.orElse(.0)

        val formatDamage = NumberFormat.getNumberInstance().format(totalBleedDamage / 2.0)

        listOf(
            "<dark_red>Soaks your hands".asComponent,
            "<dark_red>as you hold it".asComponent,
            "<gray>Its drawn blood from".asComponent,
            "<gray>$formatDamage hearts...".asComponent
        )
    }

    override fun transform0(item: ItemStack): ItemStack {
        item.unsetData(DataComponentTypes.REPAIRABLE)
        item.unsetData(DataComponentTypes.ENCHANTABLE)
        item.unsetData(DataComponentTypes.ITEM_NAME)

        item.setData(DataComponentTypes.EQUIPPABLE, Equippable
            .equippable(EquipmentSlot.HEAD)
            .assetId(Key.key("minecraft", "gold"))
            .damageOnHurt(false)
            .dispensable(false)
        )
        item.setData(DataComponentTypes.ITEM_MODEL, ItemType.GOLDEN_HELMET.key())
        item.setData(DataComponentTypes.MAX_DAMAGE, MAX_BLEED_DAMAGE.toInt())
        val bleedDamage = getPersistentData(item) {
            getDouble("total_bleed_damage")
        }.orElse(.0)
        item.setData(DataComponentTypes.DAMAGE, bleedDamage.toInt())
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
            .addHiddenComponents(DataComponentTypes.DAMAGE)
            .build()
        )

        return item
    }

    const val BLEED_RADIUS = 7.5 // 15 block aoe
    const val BLEED_RATIO = 0.08 // eight percent of health
    const val BEARER_BLEED_RATIO = 0.02
    const val MAX_BLEED_DAMAGE = 1000.0 // you can damage any number of entities a total of 1000 times

    val BLEED_PARTICLE = ParticleBuilder(Particle.FALLING_DUST)
        .extra(.0)
        .count(10)
        .force(true)

    fun playSoundAndParticles(entity: LivingEntity) {
        entity.world.playSound(SoundEventKeys.ENTITY_SLIME_ATTACK, entity.location, 0.2f, 1.6f)
        BLEED_PARTICLE
            .data(BlockType.STRIPPED_MANGROVE_LOG.createBlockData())
            .location(entity.location.add(.0, entity.height / 2f, .0))
            .receivers(100, true)
            .offset(entity.width / 4f, entity.height / 5f, entity.width / 4f)
            .spawn()
    }

    init {
        CoroutineScope(Dispatchers.Munky).launch {
            while (isActive) {
                delayTicks(15)
                val crownBearers = Bukkit.getOnlinePlayers()
                    .associateWith { it.equipment.helmet }
                    .filterValues {
                        it.kItem is CrimsonCrown
                    }
                for (e in crownBearers) {
                    val crown = e.value
                    val bearer = e.key
                    val nearbyDamage = withContext(Dispatchers.Sync) {
                        var damage = 0.0
                        val nearby =  bearer.world.getNearbyLivingEntities(bearer.location, BLEED_RADIUS).filterNot { it == bearer }
                        // dont want to tick the bearer every second, that's annoying
                        bearer.health -= (bearer.health * BEARER_BLEED_RATIO)
                        playSoundAndParticles(bearer)
                        for (near in nearby) {
                            val toDamage = near.health * BLEED_RATIO
                            near.damage(toDamage, bearer)
                            damage += toDamage
                            playSoundAndParticles(near)
                        }
                        damage
                    }
                    val totalBleedDamage = getPersistentData(crown) {
                        getDouble("total_bleed_damage")
                    }.orElse(.0) + nearbyDamage
                    editPersistentData(crown) {
                        putDouble("total_bleed_damage", totalBleedDamage)
                    }
                    if (MAX_BLEED_DAMAGE <= totalBleedDamage) {
                        bearer.equipment.helmet = null
                        bearer.playSound(SoundEventKeys.ENTITY_WARDEN_DEATH, 1f, 0.7f)
                    }
                    transform(crown)
                }
            }
        }
        initialize()
    }
}