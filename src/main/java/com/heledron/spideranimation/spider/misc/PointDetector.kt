package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.body.Leg
import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.deprecated.lookingAtPoint
import org.bukkit.Location
import org.bukkit.entity.Player

class PointDetector {
    var selectedLeg: Leg? = null
    var player: Player? = null
}

fun setupPointDetector(app: ECS) {
    fun getLeg(spider: SpiderBody, location: Location): Leg? {
        if (spider.world != location.world) return null

        val rayOrigin = location.toVector()
        val rayDirection = location.direction

        val lerpedGait = spider.lerpedGait()
        for (leg in spider.legs) {
            val lookingAt = lookingAtPoint(rayOrigin, rayDirection, leg.endEffector, lerpedGait.bodyHeight * .15)
            if (lookingAt) return leg
        }
        return null
    }


    app.onTick {
        for ((spider, pointDetector) in app.query<SpiderBody, PointDetector>()) {
            val player = pointDetector.player
            pointDetector.selectedLeg = if (player !== null) getLeg(spider, player.eyeLocation) else null
        }
    }
}