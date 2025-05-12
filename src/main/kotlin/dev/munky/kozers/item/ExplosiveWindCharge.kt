package dev.munky.kozers.item

import dev.munky.kozers.util.Munky
import dev.munky.kozers.util.Sync
import dev.munky.kozers.util.asComponent
import dev.munky.kozers.util.logger
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameEvent
import org.bukkit.NamespacedKey
import org.bukkit.entity.Explosive
import org.bukkit.entity.WindCharge
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.GenericGameEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object ExplosiveWindCharge : KItem() {
    override val name: (ItemStack) -> Component = {
        "<dark_gray>Grenade".asComponent
    }

    override val description: (ItemStack) -> List<Component> = {
        listOf(
            "<gray>Does what you think.".asComponent,
        )
    }

    override fun transform0(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16)
        item.setData(DataComponentTypes.ITEM_MODEL, ItemType.ARMADILLO_SCUTE.key())

        return item
    }

    private val TRACKER_PDC_KEY = NamespacedKey("kozers", "explosive_wind_charge.owner")

    private val logger = logger<ExplosiveWindCharge>()

    init {
        handle(PlayerInteractEvent::class) {
            logger.info("Heard player interact event $it.")
            val item = it.item ?: return@handle
            if (item.kItem != ExplosiveWindCharge) return@handle
            logger.info("Heard player interact event with ExplosiveWindCharge.")
            val player = it.player
            val direction = player.location.direction.clone()
            player.inventory.removeItem(item.clone().apply { amount = 1 })
            player.world.spawn(player.eyeLocation.add(direction.clone().multiply(0.1)), WindCharge::class.java) { entity ->
                entity.acceleration = direction
                entity.persistentDataContainer.set(
                    TRACKER_PDC_KEY,
                    PersistentDataType.LONG_ARRAY,
                    longArrayOf(player.uniqueId.leastSignificantBits, player.uniqueId.mostSignificantBits)
                )
            }
        }
        handle(GenericGameEvent::class) {
            if (it.event != GameEvent.EXPLODE) return@handle
            val windCharge = it.entity as? WindCharge ?: return@handle
            val ownerBits = windCharge.persistentDataContainer.get(TRACKER_PDC_KEY, PersistentDataType.LONG_ARRAY) ?: return@handle
            if (ownerBits.size != 2) error("Owner uuid long array must be of length 2 (128 bits).")
            val ownerId = UUID(ownerBits[0], ownerBits[1])
            val player = Bukkit.getPlayer(ownerId) ?: return@handle // player offline
            windCharge.world.createExplosion(player, windCharge.location, 4f)
        }
    }
}