package com.heledron.spideranimation.utilities.events

import com.heledron.spideranimation.utilities.currentPlugin
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.io.Closeable

fun addEventListener(listener: Listener): Closeable {
    val plugin = currentPlugin
    plugin.server.pluginManager.registerEvents(listener, plugin)
    return Closeable {
        HandlerList.unregisterAll(listener)
    }
}

fun onInteractEntity(listener: (Player, Entity, EquipmentSlot) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @EventHandler
        fun onInteract(event: PlayerInteractEntityEvent) {
            listener(event.player, event.rightClicked, event.hand)
        }
    })
}


fun onInteractEntity(listener: (event: PlayerInteractEntityEvent) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @EventHandler
        fun onInteract(event: PlayerInteractEntityEvent) {
            listener(event)
        }
    })
}

fun onSpawnEntity(listener: (Entity) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @EventHandler
        fun onSpawn(event: EntitySpawnEvent) {
            listener(event.entity)
        }
    })
}

fun onGestureUseItem(listener: (Player, ItemStack) -> Unit) = addEventListener(object : Listener {
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.action == Action.RIGHT_CLICK_BLOCK && !(event.clickedBlock?.type?.isInteractable == false || event.player.isSneaking)) return
        listener(event.player, event.item ?: return)
    }
})