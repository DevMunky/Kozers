package dev.munky.kozers.item

import dev.munky.kozers.KozersPlugin
import dev.munky.kozers.util.*
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.util.Tick
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryInteractEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataAdapterContext
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * [  ITEM_NAME  ]
 * |             |
 * [             ]
 */
abstract class KItem {
    abstract val name: (ItemStack) -> Component
    abstract val description: (ItemStack) -> List<Component>

    private val eventHandlers = HashSet<EventHandler>()
    private val listeners = HashSet<Listener>()

    fun <T: Event> handle(clazz: KClass<T>, block: (T) -> Unit) {
        eventHandlers += EventHandler(clazz, block as (Event) -> Unit)
    }

    fun initialize() {
        listeners.forEach(Listener::unregister)
        listeners.clear()
        for (e in eventHandlers) {
            listeners += listenTo(e.clazz.java, EventPriority.NORMAL) { c, l -> e.block(c) }
        }
    }

    fun transform(item: ItemStack): ItemStack {
        val readItem = item.persistentDataContainer.get(PDC_KEY, PersistentDataType)
        if (readItem != this) return item // dont transform item that is not this one.

        item.editPersistentDataContainer {
            it.set(PDC_KEY, PersistentDataType, this)
        }

        val lore = ArrayList<Component>().apply {
            repeat(2) {
                this += Component.empty()
            }
            for (line in description(item)) {
                this += center(line).decoration(TextDecoration.ITALIC, false)
            }
        }

        try {
            item.unsetData(DataComponentTypes.CONSUMABLE)
            item.unsetData(DataComponentTypes.FOOD)
            item.unsetData(DataComponentTypes.REPAIRABLE)
            item.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
            item.setData(DataComponentTypes.REPAIR_COST, 1242424)
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1)
            item.setData(DataComponentTypes.CUSTOM_NAME, center(name(item)).decoration(TextDecoration.ITALIC, false))
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore))
        }catch (t: Throwable) {
            logger.error("While changing components", t)
        }

        return transform0(item)
    }

    protected abstract fun transform0(item: ItemStack): ItemStack

    private fun center(text: Component, width: Int = 30): Component {
        require(width > 1) { "Cannot have a width less than 0." }
        var side = ""
        val asString = text.asString
        val length = asString.length
        if (length > width) {
            val mini = text.asMini
            val before = mini.substring(0..<width).asComponent
            val after = mini.substring(width - 1).asComponent
            return center(before).appendNewline() + center(after)
        }
        repeat((width / 2) - (length / 2)) {
            side += " "
        }
        var leftSide = "$side "
        repeat(((1.0 / length.toDouble()) * 25.0).toInt()) {
            leftSide += " "
        }
        return Component.empty().append(leftSide.asComponent).append(text).append(side.asComponent)
    }

    data class EventHandler(val clazz: KClass<out Event>, val block: (Event) -> Unit) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EventHandler) return false
            return clazz == other.clazz
        }

        override fun hashCode(): Int {
            return clazz.hashCode()
        }
    }

    fun createItem(): ItemStack {
        val i = ItemStack.of(Material.COD)
        i.editPersistentDataContainer {
            it.set(PDC_KEY, PersistentDataType, this)
        }
        return transform(i)
    }

    init {
        handle(InventoryInteractEvent::class) {
            val inventory = it.inventory
            if (inventory !is AnvilInventory) return@handle
            if (it.result == Event.Result.DENY) return@handle
            inventory.result?.kItem ?: return@handle
            // getting here means a kitem is in the result slot.
            it.isCancelled = true
        }
    }

    companion object {
        private val logger = logger<KItem>()

        val PDC_KEY = NamespacedKey.fromString("kozers:kitem")!!

        object PersistentDataType : org.bukkit.persistence.PersistentDataType<String, KItem> {
            private val cache = HashMap<String, KItem>()

            override fun getPrimitiveType(): Class<String> = String::class.java
            override fun getComplexType(): Class<KItem> = KItem::class.java

            override fun fromPrimitive(primitive: String, context: PersistentDataAdapterContext): KItem {
                return cache[primitive] ?: run {
                    val clazz = Class.forName(primitive, true, KozersPlugin::class.java.classLoader).kotlin
                    if (!clazz.isSubclassOf(KItem::class)) error("Class $primitive in pdc tag was expected to be subclass of KItem yet was not.")
                    return getKItemFromClass(clazz as KClass<KItem>)
                }
            }

            override fun toPrimitive(complex: KItem, context: PersistentDataAdapterContext): String = complex::class.java.name
        }

        val ITEMS = listOf( // just for commands. Actual KItems do a class lookup to get the instance.
            Ambrosia::class,
            CrimsonCrown::class
        )

        fun getKItem(item: ItemStack): KItem? {
            return item.persistentDataContainer.get(PDC_KEY, PersistentDataType)
        }

        fun <T: KItem> getKItemFromClass(clazz: KClass<T>): T {
            if (!clazz.isSubclassOf(KItem::class)) error("Class ${clazz.qualifiedName} in pdc tag was expected to be subclass of KItem yet was not.")
            return try {
                if (clazz.objectInstance != null) clazz.objectInstance!!
                else clazz.primaryConstructor?.call() ?: error("KItems must be objects or have a no-arg primary constructor")
            } catch (t: Throwable) {
                throw IllegalStateException("KItem deserialization error.", t)
            }
        }

        fun editPersistentData(item: ItemStack, block: CompoundTag.() -> Unit) {
            val nmsItem = (item as CraftItemStack).handle
            val tag = nmsItem.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
            tag.block()
            nmsItem.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        }

        fun <T> getPersistentData(item: ItemStack, block: CompoundTag.() -> T): T {
            val nmsItem = (item as CraftItemStack).handle
            val tag = nmsItem.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
            return tag.block()
        }
    }
}

val ItemStack.kItem: KItem? get() = KItem.getKItem(this)

/**
 * Checks if the cooldown is complete, and it is start the cooldown.
 */
fun Player.isCooldownComplete(item: ItemStack): Boolean {
    if (this.isOp) return true // debugging
    val cd = item.getData(DataComponentTypes.USE_COOLDOWN) ?: return true
    val group = cd.cooldownGroup() ?: return true
    val timeRemaining = getCooldown(group)
    if (timeRemaining >= 1) return false
    setCooldown(group, Tick.tick().fromDuration(cd.seconds().toInt().seconds.toJavaDuration()))
    return true
}