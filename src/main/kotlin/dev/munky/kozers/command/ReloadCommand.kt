package dev.munky.kozers.command

import com.mojang.brigadier.tree.LiteralCommandNode
import dev.munky.kozers.plugin
import dev.munky.kozers.util.asComponent
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

object ReloadCommand : Command {
    override fun build(): LiteralCommandNode<CommandSourceStack> = Commands
        .literal("reload")
        .executes {
            plugin.reload()
            it.source.sender.sendMessage("<green>Successfully reloaded Kozers".asComponent)
            Command.SINGLE_SUCCESS
        }
        .build()
}