package dev.munky.kozers.item

import dev.munky.kozers.util.asComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.FoodProperties
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import io.papermc.paper.registry.keys.SoundEventKeys
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object ReesesCup : KItem() {
    override val name: (ItemStack) -> Component = {
        "<#be893f>Reeses Cup".asComponent
    }
    override val description: (ItemStack) -> List<Component> = {
        listOf(
            "<#be893f>It's yummy.".asComponent
        )
    }

    private const val DURATION_IN_TICKS = 20 * 20

    override fun transform0(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16)
        item.setData(DataComponentTypes.ITEM_MODEL, ItemType.MUSHROOM_STEW.key())

        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
            .addEffect(ConsumeEffect.playSoundConsumeEffect(SoundEventKeys.ENTITY_ENDER_DRAGON_GROWL))
            .addEffect(
                ConsumeEffect.applyStatusEffects(listOf(
                PotionEffect(PotionEffectType.ABSORPTION, DURATION_IN_TICKS, 1),
                PotionEffect(PotionEffectType.RESISTANCE, DURATION_IN_TICKS, 1),
                PotionEffect(PotionEffectType.SPEED, DURATION_IN_TICKS, 1),
                PotionEffect(PotionEffectType.INSTANT_HEALTH, DURATION_IN_TICKS, 255),
                PotionEffect(PotionEffectType.NAUSEA, (DURATION_IN_TICKS * 0.5).toInt(), 2)
            ), 1f))
        )
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().canAlwaysEat(true).nutrition(4).saturation(6f))

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
            .addHiddenComponents(DataComponentTypes.FOOD, DataComponentTypes.CONSUMABLE)
        )

        return item
    }
}