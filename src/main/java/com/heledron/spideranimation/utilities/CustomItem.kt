package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.SpiderAnimationPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

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
                val customItem = get(player.inventory.itemInMainHand) ?: continue
                if (customItem.isItem(player.inventory.itemInMainHand)) customItem.onHeldTick?.invoke(player)
            }
        }
    }
}


fun createNamedItem(material: org.bukkit.Material, name: String): ItemStack {
    val item = ItemStack(material)
    val itemMeta = item.itemMeta ?: throw Exception("ItemMeta is null")
    itemMeta.setItemName(ChatColor.RESET.toString() + name)
    item.itemMeta = itemMeta
    return item
}