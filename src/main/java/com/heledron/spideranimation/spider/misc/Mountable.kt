package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.events.addEventListener
import com.heledron.spideranimation.utilities.events.onInteractEntity
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.maths.yawRadians
import com.heledron.spideranimation.utilities.rendering.RenderEntity
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

@Suppress("UnstableApiUsage")
class Mountable(val spider: Spider): SpiderComponent {
    var currentMarker: ArmorStand? = null
    var currentPig: Pig? = null
    var closable = mutableListOf<Closeable>()

    fun getRider() = currentMarker?.passengers?.firstOrNull() as? Player

    init {
        closable += onInteractEntity { player, entity, hand ->
            val currentPig = currentPig ?: return@onInteractEntity
            if (entity != currentPig) return@onInteractEntity
            if (hand != EquipmentSlot.HAND) return@onInteractEntity

            // if right click with saddle, add saddle (automatic)
            if (player.inventory.itemInMainHand.type == Material.SADDLE && !currentPig.hasSaddle()) {
                playSound(currentPig.location, Sound.ENTITY_PIG_SADDLE, 1.0f, 1.0f)
            }

            // if right click with empty hand, remove saddle
            if (player.inventory.itemInMainHand.type.isAir && getRider() == null) {
                if (player.isSneaking) {
                    currentPig.setSaddle(false)
                }
            }
        }

        // when player mounts the pig, switch them to the marker entity
        closable += addEventListener(object : Listener {
            @EventHandler
            fun onMount(event: VehicleEnterEvent) {
                val currentPig = currentPig ?: return
                val currentMarker = currentMarker ?: return

                if (event.vehicle != currentPig) return
                val player = event.entered

                event.isCancelled = true
                currentMarker.addPassenger(player)
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

        RenderEntity(
            clazz = Pig::class.java,
            location = pigLocation,
            init = {
                it.setGravity(false)
                it.setAI(false)
                it.isInvisible = true
                it.isInvulnerable = true
                it.isSilent = true
                it.isCollidable = false
            },
            update = {
                currentPig = it
            }
        ).submit(spider to "mountable.pig")

        RenderEntity(
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
                currentMarker = it
                if (getRider() == null) return@update

                // This is the only way to preserve passengers when teleporting.
                // Paper has a TeleportFlag, but it is not supported by Spigot.
                // https://jd.papermc.io/paper/1.21/io/papermc/paper/entity/TeleportFlag.EntityState.html
                runCommandSilently("execute as ${it.uniqueId} at @s run tp ${markerLocation.x} ${markerLocation.y} ${markerLocation.z}")
            }
        ).submit(spider to "mountable.marker")
    }

    override fun close() {
        closable.forEach { it.close() }
    }
}