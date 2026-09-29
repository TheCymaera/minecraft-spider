package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.Leg
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.lookingAtPoint
import com.heledron.spideranimation.utilities.overloads.direction
import com.heledron.spideranimation.utilities.overloads.eyePosition
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class PointDetector : ECSComponent {
    var checkPlayers = setOf<Player>()
    val selectedLeg = mutableMapOf<Player, Leg>()
}

fun setupPointDetector(app: ECS) {
    fun rayCastLeg(spider: SpiderBody, options: SpiderOptions, world: World, rayOrigin: Vector, rayDirection: Vector): Leg? {
        if (spider.world != world) return null

        val tolerance = options.walkGait.stationary.bodyHeight * .15
        for (leg in spider.legs) {
            val lookingAt = lookingAtPoint(rayOrigin, rayDirection, leg.endEffector, tolerance)
            if (lookingAt) return leg
        }
        return null
    }

    app.onTick {
        for ((spider, pointDetector, options) in app.query<SpiderBody, PointDetector, SpiderOptions>()) {
            pointDetector.selectedLeg.clear()

            for (player in pointDetector.checkPlayers) {
                val leg = rayCastLeg(spider, options, player.world, player.eyePosition, player.direction) ?: continue
                pointDetector.selectedLeg[player] = leg
            }
        }
    }
}