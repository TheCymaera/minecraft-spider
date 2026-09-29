package com.heledron.spideranimation.laser

import com.heledron.spideranimation.utilities.rendering.renderBlock
import com.heledron.spideranimation.spider.components.SpiderBehaviour
import com.heledron.spideranimation.spider.components.TargetBehaviour
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.centredTransform
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

            val distance = options.walkGait.stationary.bodyHeight * 2
            val behaviour = TargetBehaviour(nearestLaser.position, distance)
            spiderEntity.replaceComponent<SpiderBehaviour>(behaviour)
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