package dev.munky.kozers.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import dev.munky.kozers.util.asComponent
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

interface Command {
    fun build(): LiteralCommandNode<CommandSourceStack>

    companion object {
        const val SINGLE_SUCCESS = Command.SINGLE_SUCCESS
        val ARGUMENT_SCOPE = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher() + SupervisorJob())
        fun fail(minimessage: String): Nothing {
            throw SimpleCommandExceptionType(PaperAdventure.asVanilla(minimessage.asComponent)).create()
        }
    }
}