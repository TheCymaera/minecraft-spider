package com.heledron.spideranimation.utilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class CustomItem(
    val id: String,
    val onRightClick: ((player: Player) -> Unit)? = null,
    val onHeldTick: ((player: Player) -> Unit)? = null,
    defaultItem: ItemStack
) {
    val defaultItem = defaultItem.apply { attach(this) }

    fun attach(item: ItemStack) {
        val itemMeta = item.itemMeta ?: throw Exception("ItemMeta is null")
        itemMeta.persistentDataContainer.set(CustomItemRegistry.ID_KEY, PersistentDataType.STRING, id)
        item.itemMeta = itemMeta
    }

    fun isItem(item: ItemStack): Boolean {
        return item.itemMeta?.persistentDataContainer?.get(CustomItemRegistry.ID_KEY, PersistentDataType.STRING) == id
    }
}


object CustomItemRegistry {
    val ID_KEY; get () = NamespacedKey(currentPlugin, "item_id")

    val items = mutableListOf<CustomItem>()

    fun get(item: ItemStack): CustomItem? {
        return items.find { it.isItem(item) }
    }

    init {
        onGestureUseItem { player, item ->
            val customItem = get(item) ?: return@onGestureUseItem
            if (customItem.isItem(item)) customItem.onRightClick?.invoke(player)
        }

        onTick {
            for (player in Bukkit.getServer().onlinePlayers) {
                get(player.inventory.itemInMainHand)?.onHeldTick?.invoke(player)
                get(player.inventory.itemInOffHand)?.onHeldTick?.invoke(player)
            }
        }
    }
}


fun createNamedItem(material: org.bukkit.Material, name: String): ItemStack {
    val item = ItemStack(material)
    // Use .editMeta for a safer and more concise way to modify meta
    item.editMeta { itemMeta ->
        // Create a text component and disable the default italic style
        val nameComponent = Component.text(name).decoration(TextDecoration.ITALIC, false)

        // Set the component as the item's display name
        itemMeta.displayName(nameComponent)
    }
    return item
}