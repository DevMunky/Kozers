package dev.munky.kozers.item

import dev.munky.kozers.KozersPlugin
import dev.munky.kozers.util.asComponent
import dev.munky.kozers.util.asString
import dev.munky.kozers.util.listenTo
import dev.munky.kozers.util.unregister
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.item.component.CustomData
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataAdapterContext
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.time.measureTimedValue

@Serializable
data class KItemKey(val category: KItem.Category, val id: String) {
    object PersistentDataType: org.bukkit.persistence.PersistentDataType<String, KItemKey>{
        override fun getPrimitiveType(): Class<String> = String::class.java
        override fun getComplexType(): Class<KItemKey> = KItemKey::class.java

        override fun fromPrimitive(primitive: String, context: PersistentDataAdapterContext): KItemKey {
            val split = primitive.split("/")
            val c = enumValueOf<KItem.Category>(split[0])
            return KItemKey(c, split[1])
        }

        override fun toPrimitive(complex: KItemKey, context: PersistentDataAdapterContext): String {
            return "${complex.category}/${complex.id}"
        }
    }

    companion object {
        val PDC_KEY = NamespacedKey.fromString("kozers:key")!!
    }
}

/**
 * [  ITEM_NAME  ]
 * |             |
 * [             ]
 */
abstract class KItem: ItemStack(of(Material.COD)) {
    abstract val key: KItemKey
    abstract val name: Component
    abstract val description: List<Component>

    private val eventHandlers = HashSet<EventHandler>()
    private val listeners = HashSet<Listener>()

    data class EventHandler(val clazz: KClass<out Event>, val block: (Event) -> Unit)

    fun <T: Event> handle(clazz: KClass<T>, block: (T) -> Unit) {
        eventHandlers += EventHandler(clazz, block as (Event) -> Unit)
    }

    val handle get() = (CRAFT_DELEGATE_FIELD.get(this) as CraftItemStack).handle

    fun initialize() {
        val cd = handle.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        cd.put("kozers:key", StringTag.valueOf("${key.category}/${key.id}"))
        cd.put("kozers:item_class", StringTag.valueOf(this::class.java.name))
        handle.set(DataComponents.CUSTOM_DATA, CustomData.of(cd))

        setData(DataComponentTypes.CUSTOM_NAME, center(name))

        val lore = ArrayList<Component>().apply {
            repeat(3) {
                this += Component.empty()
            }
            for (line in description) {
                this += center(line.style(Style.style(TextDecoration.ITALIC)).color(NamedTextColor.GRAY))
            }
        }

        setData(DataComponentTypes.LORE, ItemLore.lore(lore))

        for (handler in eventHandlers) {
            listeners += listenTo(handler.clazz.java, EventPriority.NORMAL) { e, l -> handler.block(e) }
        }
    }

    fun teardown() {
        for (l in listeners) l.unregister()
    }

    private fun center(text: Component, width: Int = 40): Component {
        var side = ""
        repeat((width / 2) - (text.asString.length / 2)) {
           side += " "
        }
        val sideText = side.asComponent
        return Component.empty().append(sideText).append(text).append(sideText)
    }

    enum class Category {
        WEAPON,
        UTILITY,
        ARMOR
    }

    companion object {
        val CRAFT_DELEGATE_FIELD: VarHandle = MethodHandles.privateLookupIn(
            ItemStack::class.java,
            MethodHandles.lookup()
        ).findVarHandle(ItemStack::class.java, "craftDelegate", ItemStack::class.java)

        val ITEMS = listOf<KItem>( // just for commands. Actual KItems do a class lookup to get the instance.
            Ambrosia
        )

        fun getKItem(item: ItemStack): KItem? {
            var kitem = item as? KItem
            if (kitem == null) {
                val craft = if (item is CraftItemStack) item else CRAFT_DELEGATE_FIELD.get(item) as CraftItemStack
                val handle = craft.handle ?: return null
                kitem = getKItemFromNMS(handle)
            }
            return kitem
        }

        fun getKItemFromNMS(item: net.minecraft.world.item.ItemStack): KItem? {
            val cd = item.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
            val clas = cd.getString("kozers:item_class").getOrNull() ?: return null
            // slow route, should not have to do this because KItems are instantiated upon deserialization
            // Only slow if the item is given through a command and the class lookup couldn't get cached by the vm.
            return Class.forName(clas, true, KozersPlugin::class.java.classLoader).kotlin.objectInstance as KItem
        }
    }
}

val ItemStack.kItem: KItem? get() = KItem.getKItem(this)