package com.heledron.spideranimation.items

import com.heledron.spideranimation.SpiderAnimationPlugin
import com.heledron.spideranimation.interval
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemStack

class CustomItem(
    val id: String,
    val onRightClick: ((player: Player) -> Unit)? = null,
    val onHeldTick: ((player: Player) -> Unit)? = null,
    defaultItem: ItemStack
) {
    val defaultItem = defaultItem.apply { attach(this) }

    fun attach(item: ItemStack) {
        val itemMeta = item.itemMeta ?: throw Exception("ItemMeta is null")
        itemMeta.persistentDataContainer.set(CustomItemRegistry.ID_KEY, org.bukkit.persistence.PersistentDataType.STRING, id)
        item.itemMeta = itemMeta
    }

    fun isItem(item: ItemStack): Boolean {
        return item.itemMeta?.persistentDataContainer?.get(CustomItemRegistry.ID_KEY, org.bukkit.persistence.PersistentDataType.STRING) == id
    }
}


object CustomItemRegistry {
    val ID_KEY = NamespacedKey.fromString("heledron_custom_items:id") ?: throw Exception("Invalid key")

    val items = mutableListOf<CustomItem>()

    fun get(item: ItemStack): CustomItem? {
        return items.find { it.isItem(item) }
    }

    fun initialize() {
        val server = org.bukkit.Bukkit.getServer()

        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
                if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
                if (event.action == Action.RIGHT_CLICK_BLOCK && !(event.clickedBlock?.type?.isInteractable == false || event.player.isSneaking)) return
                val customItem = get(event.item ?: return) ?: return

                if (customItem.isItem(event.item ?: return)) customItem.onRightClick?.invoke(event.player)
            }
        }, SpiderAnimationPlugin.instance)

        interval(0, 1) {
            for (player in server.onlinePlayers) {
                val customItem = get(player.inventory.itemInMainHand) ?: continue
                if (customItem.isItem(player.inventory.itemInMainHand)) customItem.onHeldTick?.invoke(player)
            }
        }
    }
}