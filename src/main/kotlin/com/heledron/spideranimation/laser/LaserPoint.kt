package com.heledron.spideranimation.laser

import com.heledron.spideranimation.utilities.rendering.renderBlock
import com.heledron.spideranimation.spider.components.Locomotion
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.centredTransform
import com.heledron.spideranimation.utilities.horizontalDistance
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector

class LaserPoint(
    var world: World,
    var position: Vector,
    var isVisible: Boolean,
) : ECSComponent

fun setupLaserPointer(app: ECS) {
    app.onTick {
        val lasers = app.query<LaserPoint>()

        // get spiders to follow the laser
        for ((spiderEntity, spider, options) in app.query<ECSEntity, SpiderBody, SpiderOptions>()) {
            val nearestLaser = lasers
                .filter { it.world == spider.world }
                .minByOrNull { it.position.distanceSquared(spider.position) }
                ?: continue

            val locomotion = spiderEntity.query<Locomotion>() ?: continue

            val direction = nearestLaser.position.clone().subtract(spider.position).normalize()
            locomotion.face(direction, Locomotion.LASER_PRIORITY)

            // only walk while far enough away to stop in time
            val distance = options.walkGait.stationary.bodyHeight * 2
            val currentSpeed = spider.velocity.length()
            val decelerateDistance = (currentSpeed * currentSpeed) / (2 * options.gait.moveAcceleration)
            val currentDistance = spider.position.horizontalDistance(nearestLaser.position)

            val walkVelocity = if (currentDistance > distance + decelerateDistance) {
                direction.clone().multiply(options.gait.maxSpeed)
            } else {
                Vector(0.0, 0.0, 0.0)
            }

            locomotion.walk(walkVelocity, Locomotion.LASER_PRIORITY)
        }
    }


    app.onRender {
        val size = .25f
        for (laser in app.query<LaserPoint>()) {
            if (!laser.isVisible) continue
            renderLaserPoint(laser.world, laser.position, size).submit(laser)
        }
    }
}

private fun renderLaserPoint(
    world: World,
    position: Vector,
    size: Float,
) = renderBlock(
    world = world,
    position = position,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(size, size, size)
    }
)