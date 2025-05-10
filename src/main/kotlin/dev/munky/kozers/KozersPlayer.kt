package dev.munky.kozers

import dev.munky.kozers.serialization.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.lang.ref.WeakReference
import java.util.*

@Serializable
data class KozersPlayer(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    @Contextual val lastKnownLocation: Location
) {
    @Transient private var _player = WeakReference<Player>(null)
    val player: Player get() {
        if (_player.refersTo(null)) { _player = WeakReference(Bukkit.getPlayer(uuid)) }
        return _player.get()!!
    }

    constructor(
        player: Player,
        lastKnownLocation: Location
    ): this(player.uniqueId, lastKnownLocation) {
        _player = WeakReference(player)
    }
}