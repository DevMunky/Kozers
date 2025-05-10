package dev.munky.kozers.manager

import dev.munky.kozers.plugin
import dev.munky.kozers.util.listenTo
import dev.munky.kozers.util.asComponent
import dev.munky.kozers.util.text
import dev.munky.kozers.util.unregister
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.luckperms.api.LuckPerms
import net.luckperms.api.node.types.PrefixNode
import org.bukkit.Bukkit
import java.io.Closeable

class ChatManager : Closeable {
    private val chatEvent = listenTo<AsyncChatEvent> { event ->
        val kozers = plugin.playerManager[event.player]
        val lp = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)!!.provider
        val user = lp.userManager.getUser(event.player.uniqueId)!!
        val prefix = user.nodes.filterIsInstance<PrefixNode>().maxByOrNull { n -> n.priority }?.metaValue ?: "<gray>None</gray>"
        event.player.playerListName(Component.empty()
            .append(prefix.asComponent)
            .append(text(" "))
            .append(event.player.displayName())
        )
        event.renderer { source, sourceDisplayName, message, viewer ->
            Component.empty()
                // rank
                .append(prefix.asComponent)
                .append(text(" "))
                // name
                .append(sourceDisplayName)
                // message lead
                .append(text(" > ").color(TextColor.color(255, 0, 0)))
                // message
                .append(message)
        }
    }

    override fun close() {
        chatEvent.unregister()
    }
}