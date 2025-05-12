package dev.munky.kozers.serialization

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import dev.munky.kozers.item.KItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*

val SERIALIZERS_MODULE = SerializersModule {
    contextual(UUID::class, UUIDSerializer)
    contextual(ItemStack::class, ItemStackSerializer)
    contextual(Location::class, LocationSerializer)
    contextual(Vector::class, VectorSerializer)
}

val Json: Json = Json {
    classDiscriminator = "type"
    allowStructuredMapKeys = true
    serializersModule = SERIALIZERS_MODULE
    prettyPrint = true
}

val Toml: Toml by lazy {
    Toml(
        serializersModule = SERIALIZERS_MODULE,
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true
        )
    )
}

object OfflinePlayerSerializer : KSerializer<OfflinePlayer> by UUIDSerializer.xmap(OfflinePlayer::getUniqueId, Bukkit::getOfflinePlayer)

object LocationSerializer : KSerializer<Location> by codec(
    "world", { world?.uid }, UUIDSerializer.nullable,
    "x", { x }, Double.serializer(),
    "y", { y }, Double.serializer(),
    "z", { z }, Double.serializer(),
    "yaw", { if (yaw in -.5f..(.5f)) null else yaw }, Float.serializer().nullable,
    "pitch", { if (pitch in -.01f..(.01f)) null else pitch }, Float.serializer().nullable,
    { a, b, c, d, e, f ->
        val world = if (a != null) Bukkit.getWorld(a) else null
        Location(world, b, c, d, e ?: 0f, f ?: 0f)
    }
)

object VectorSerializer: KSerializer<Vector> by codec(
    "x", { x }, Double.serializer(),
    "y", { y }, Double.serializer(),
    "z", { z }, Double.serializer(),
    ::Vector
)

object ItemStackSerializer : KSerializer<ItemStack> by JsonElement.serializer().xmap(
    {
        val item = when (this) {
            is CraftItemStack -> handle
            else -> error("Unknown item")
        }
        net.minecraft.world.item.ItemStack.CODEC.encode(item, KJsonOps, KJsonOps.empty()).orThrow
    }, {
        val item = net.minecraft.world.item.ItemStack.CODEC.decode(KJsonOps, this).orThrow.first.asBukkitMirror()
        // If this item has KItem component, then transform the item with it. If not, just return the item.
        KItem.getKItem(item)?.transform(item) ?: item
    }
)

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}