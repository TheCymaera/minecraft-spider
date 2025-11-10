package com.heledron.spideranimation.utilities.custom_entities

import com.heledron.spideranimation.utilities.events.onInteractEntity
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerInteractEntityEvent

class CustomEntityComponent private constructor(val tag: String)  {
    companion object {
        fun create(tag: NamespacedKey): CustomEntityComponent {
            return CustomEntityComponent(tag.namespace + "_" + tag.key)
        }

        fun fromString(tag: String): CustomEntityComponent {
            return CustomEntityComponent(tag)
        }
    }

    fun entities() = allEntities().filter { it.scoreboardTags.contains(tag) }

    fun isAttached(entity: Entity): Boolean {
        return entity.scoreboardTags.contains(tag)
    }

    fun onTick(action: (Entity) -> Unit) {
        com.heledron.spideranimation.utilities.events.onTick {
            entities().forEach { action(it) }
        }
    }

    fun onInteract(action: (event: PlayerInteractEntityEvent) -> Unit) {
        onInteractEntity { event ->
            if (!event.rightClicked.scoreboardTags.contains(tag)) return@onInteractEntity
            action(event)
        }
    }
}

fun allEntities() = Bukkit.getServer().worlds.flatMap { it.entities }


fun Entity.attach(component: CustomEntityComponent) {
    this.addScoreboardTag(component.tag)
}

fun Entity.detach(component: CustomEntityComponent) {
    this.removeScoreboardTag(component.tag)
}