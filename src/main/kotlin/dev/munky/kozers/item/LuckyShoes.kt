package dev.munky.kozers.item

import dev.munky.kozers.util.asComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.DyedItemColor
import io.papermc.paper.datacomponent.item.Equippable
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType

object LuckyShoes : KItem() {
    override val name: (ItemStack) -> Component = {
        "<green>Lucky Shoes".asComponent
    }
    override val description: (ItemStack) -> List<Component> = {
        listOf(
            "<green>They are lucky.".asComponent
        )
    }

    private val LUCK_MODIFIER = AttributeModifier(NamespacedKey("kozers", "luck.boots"), 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET)
    private val ARMOR_MODIFIER = AttributeModifier(NamespacedKey.minecraft("armor.boots"), 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET)

    override fun transform0(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable
            .equippable(EquipmentSlot.FEET)
            .assetId(Key.key("minecraft", "leather"))
        )
        item.setData(DataComponentTypes.ITEM_MODEL, ItemType.LEATHER_BOOTS.key())
        item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.LIME))
        item.setData(DataComponentTypes.MAX_DAMAGE, 120)
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
            .addModifier(Attribute.LUCK, LUCK_MODIFIER)
            .addModifier(Attribute.ARMOR, ARMOR_MODIFIER)
        )
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
            .addHiddenComponents(DataComponentTypes.DYED_COLOR)
        )
        return item
    }
}