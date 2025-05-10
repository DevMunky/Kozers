package dev.munky.kozers.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import dev.munky.kozers.command.Command.Companion.ARGUMENT_SCOPE
import dev.munky.kozers.command.Command.Companion.SINGLE_SUCCESS
import dev.munky.kozers.command.Command.Companion.fail
import dev.munky.kozers.item.Ambrosia
import dev.munky.kozers.item.KItem
import dev.munky.kozers.util.asComponent
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.bukkit.entity.Player

object KItemCommand: Command {
    override fun build(): LiteralCommandNode<CommandSourceStack> = Commands
        .literal("giveCustom")
        .then(Commands
            .argument("item", StringArgumentType.word())
            .suggests { context, builder ->
                ARGUMENT_SCOPE.async {
                    for (item in KItem.ITEMS) {
                        builder.suggest(item::class.simpleName)
                    }
                    builder.build()
                }.asCompletableFuture()
            }
            .executes {
                val itemName = it.getArgument("item", String::class.java)
                val player = it.source.sender as? Player ?: fail("<red>Only players may execute this command.")

                val item = KItem.ITEMS.firstOrNull { it::class.simpleName == itemName }
                    ?: fail("<red>No custom item called <white>'<gray>$itemName</gray>'</white> exists.")

                player.give(item)
                player.sendMessage("<green>Received custom item <white>'<gray>$itemName</gray>'</white>.".asComponent)
                SINGLE_SUCCESS
            }
        )
        .build()
}