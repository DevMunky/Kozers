package dev.munky.kozers.item

import dev.munky.kozers.util.asComponent
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.persistence.PersistentDataType

object Ambrosia : KItem() {
    override val key: KItemKey = KItemKey(Category.UTILITY, "ambrosia")
    override val name: Component = "<gold><italic>Ambrosia".asComponent
    override val description: List<Component> = listOf(
        "A magical food that never runs out.".asComponent,
        "Gives various buffs upon consumption".asComponent
    )

    init {
        editPersistentDataContainer {
            it.set(NamespacedKey.minecraft("test"), PersistentDataType.INTEGER_ARRAY, intArrayOf(1, 2, 3))
        }
        handle(PlayerInteractEvent::class) {
            if (it.item?.kItem != this) return@handle
            it.player.sendMessage("Ate ambrosia")
        }
        handle(PlayerMoveEvent::class) {
            if (it.player.inventory.itemInMainHand.kItem != this) return@handle
            it.from = it.from.add(it.from.toVector().normalize().multiply(0.5))
        }
        initialize()
    }
}