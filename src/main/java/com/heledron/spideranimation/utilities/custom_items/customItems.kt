package com.heledron.spideranimation.utilities.custom_items

import com.heledron.spideranimation.utilities.events.onGestureUseItem
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.namespacedID
import com.heledron.spideranimation.utilities.requireCommand
import org.bukkit.Bukkit
import org.bukkit.Bukkit.createInventory
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

val customItemRegistry = mutableListOf<ItemStack>()

fun openCustomItemInventory(player: Player) {
    val inventory = createInventory(null, 9 * 3, "Items")
    customItemRegistry.forEach { inventory.addItem(it) }
    player.openInventory(inventory)
}

fun setupCustomItemCommand() {
    requireCommand("items").apply {
        setExecutor { sender, _, _, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players.")
                return@setExecutor true
            }
            openCustomItemInventory(sender)
            true
        }
    }
}

class CustomItemComponent(val id: String) {
    fun isAttached(item: ItemStack): Boolean {
        return item.itemMeta?.persistentDataContainer?.get(namespacedID("item_component_$id"), PersistentDataType.BOOLEAN) == true
    }

    fun attach(item: ItemStack) {
        val itemMeta = item.itemMeta ?: return
        itemMeta.persistentDataContainer.set(namespacedID("item_component_$id"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = itemMeta
    }

    fun onGestureUse(action: (Player, ItemStack) -> Unit) {
        onGestureUseItem { player, item ->
            if (isAttached(item)) action(player, item)
        }
    }

    fun onInteractEntity(action: (Player, Entity, ItemStack) -> Unit) {
        com.heledron.spideranimation.utilities.events.onInteractEntity(fun(player, entity, hand) {
            val item = player.inventory.getItem(hand) ?: return
            if (isAttached(item)) action(player, entity, item)
        })
    }

    fun onHeldTick(action: (Player, ItemStack) -> Unit) {
        onTick {
            for (player in Bukkit.getServer().onlinePlayers) {
                val itemInMainHand = player.inventory.itemInMainHand
                val itemInOffHand = player.inventory.itemInOffHand
                if (isAttached(itemInMainHand)) action(player, itemInMainHand)
                if (isAttached(itemInOffHand)) action(player, itemInOffHand)
            }
        }
    }
}

fun createNamedItem(material: Material, name: String): ItemStack {
    val item = ItemStack(material)
    val itemMeta = item.itemMeta ?: throw Exception("ItemMeta is null")
    itemMeta.setItemName(ChatColor.RESET.toString() + name)
    item.itemMeta = itemMeta
    return item
}

fun ItemStack.attach(component: CustomItemComponent): ItemStack {
    component.attach(this)
    return this
}