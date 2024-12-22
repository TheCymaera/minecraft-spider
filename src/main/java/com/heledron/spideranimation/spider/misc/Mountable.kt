package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import org.joml.Quaternionf
import java.io.Closeable

class Mountable(val spider: Spider): SpiderComponent {
    val pig = SingleEntityRenderer<Pig>()
    var marker = SingleEntityRenderer<ArmorStand>()

    var closable = mutableListOf<Closeable>()

    fun getRider() = marker.entity?.passengers?.firstOrNull() as? Player

    init {
        closable += pig
        closable += marker

        closable += onInteractEntity { player, entity, hand ->
            val pigEntity = pig.entity
            if (entity != pigEntity) return@onInteractEntity
            if (hand != EquipmentSlot.HAND) return@onInteractEntity

            // if right click with saddle, add saddle (automatic)
            if (player.inventory.itemInMainHand.type == Material.SADDLE && !pigEntity.hasSaddle()) {
                playSound(pigEntity.location, Sound.ENTITY_PIG_SADDLE, 1.0f, 1.0f)
            }

            // if right click with empty hand, remove saddle
            if (player.inventory.itemInMainHand.type.isAir && getRider() == null) {
                if (player.isSneaking) {
                    pigEntity.setSaddle(false)
                }
            }
        }

        // when player mounts the pig, switch them to the marker entity
        closable += addEventListener(object : Listener {
            @EventHandler
            fun onMount(event: VehicleEnterEvent) {
                if (event.vehicle != pig.entity) return
                val player = event.entered
                val marker = marker.entity ?: return

                event.isCancelled = true
                marker.addPassenger(player)
            }
        })

        closable += onTick {
            val player = getRider() ?: return@onTick

            val input = Vector()
            if (player.currentInput.isLeft) input.x += 1.0
            if (player.currentInput.isRight) input.x -= 1.0
            if (player.currentInput.isForward) input.z += 1.0
            if (player.currentInput.isBackward) input.z -= 1.0

            val rotation = Quaternionf().rotationYXZ(player.location.yawRadians(), .0f, .0f)
            val direction = if (input.isZero) input else input.rotate(rotation).normalize()

            spider.behaviour = DirectionBehaviour(spider, player.location.direction, direction)

        }
    }

    override fun render() {
        val location = spider.location().add(spider.velocity)

        val pigLocation = location.clone().add(Vector(.0, -.6, .0))
        val markerLocation = location.clone().add(Vector(.0, .3, .0))

        pig.render(RenderEntity(
            clazz = Pig::class.java,
            location = pigLocation,
            init = {
                it.setGravity(false)
                it.setAI(false)
                it.isInvisible = true
                it.isInvulnerable = true
                it.isSilent = true
                it.isCollidable = false
            }
        ))

        marker.render(RenderEntity(
            clazz = ArmorStand::class.java,
            location = markerLocation,
            init = {
                it.setGravity(false)
                it.isInvisible = true
                it.isInvulnerable = true
                it.isSilent = true
                it.isCollidable = false
                it.isMarker = true
            },
            update = update@{
                if (getRider() == null) return@update

                // This is the only way to preserve passengers when teleporting.
                // Paper has a TeleportFlag, but it is not supported by Spigot.
                // https://jd.papermc.io/paper/1.21/io/papermc/paper/entity/TeleportFlag.EntityState.html
                runCommandSilently("execute as ${it.uniqueId} at @s run tp ${markerLocation.x} ${markerLocation.y} ${markerLocation.z}")
            }
        ))
    }

    override fun close() {
        closable.forEach { it.close() }
    }
}