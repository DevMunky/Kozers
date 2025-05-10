package dev.munky.kozers.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver
import io.papermc.paper.math.FinePosition
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

object WorldCommand : Command {
    override fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("world")
        .then(Commands
            .literal("tp")
            .then(Commands
                .argument("world", ArgumentTypes.world())
                .executes {
                    val sender = it.source.sender
                    if (sender !is Player) Command.fail("Must be a player to run this command")

                    val world = it.getArgument("world", World::class.java)
                    val position = world.spawnLocation

                    sender.sendMessage("<green>Teleported to ${position.toVector()} in world ${world.name}")
                    sender.teleport(position)

                    Command.SINGLE_SUCCESS
                }
                .then(Commands
                    .argument("position", ArgumentTypes.finePosition())
                    .executes {
                        val sender = it.source.sender
                        if (sender !is Player) Command.fail("")

                        val world = it.getArgument("world", World::class.java)
                        val position = it.getArgument("position", FinePositionResolver::class.java).resolve(it.source)

                        sender.sendMessage("<green>Teleported to ${position.toVector()} in world ${world.name}")
                        sender.teleport(Location(world, position.x(), position.y(), position.z()))

                        Command.SINGLE_SUCCESS
                    }
                )
                .then(Commands
                    .argument("player1", ArgumentTypes.player()).executes {
                        val sender = it.source.sender
                        if (sender !is Player) Command.fail("Must be a player to run this command")

                        val world = it.getArgument("world", World::class.java)
                        val position = world.spawnLocation
                        val target = it.getArgument("player1", Player::class.java)

                        sender.sendMessage("<green>Teleported '${target.name}' to ${position.toVector()} in world ${world.name}")
                        target.teleport(Location(world, position.x(), position.y(), position.z()))

                        Command.SINGLE_SUCCESS
                    }
                    .then(Commands
                        .argument("position", ArgumentTypes.finePosition())
                        .executes {
                            val sender = it.source.sender
                            if (sender !is Player) Command.fail("Must be a player to run this command")

                            val world = it.getArgument("world", World::class.java)
                            val position = it.getArgument("position", FinePositionResolver::class.java).resolve(it.source)
                            val target = it.getArgument("player1", Player::class.java)

                            sender.sendMessage("<green>Teleported '${target.name}' to ${position.toVector()} in world ${world.name}")
                            target.teleport(Location(world, position.x(), position.y(), position.z()))

                            Command.SINGLE_SUCCESS
                        }
                    )
                )
            )
        )
        .build()
}