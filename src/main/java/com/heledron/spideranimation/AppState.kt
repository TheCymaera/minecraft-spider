package com.heledron.spideranimation

import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.spider.misc.Cloak
import com.heledron.spideranimation.spider.misc.Mountable
import com.heledron.spideranimation.spider.misc.PointDetector
import com.heledron.spideranimation.spider.misc.SoundsAndParticles
import com.heledron.spideranimation.spider.misc.TridentHitDetector
import com.heledron.spideranimation.spider.presets.hexBot
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.ECSEntity
import org.bukkit.Location

object AppState {
    var options = hexBot(4, 1.0)
    var miscOptions = MiscellaneousOptions()
    var renderDebugVisuals = false

    var gallop = false

    val ecs = ECS()

    var target: Location? = null

    fun createSpider(location: Location): ECSEntity {
        location.y += options.walkGait.stationary.bodyHeight
        return ecs.spawn(
            SpiderBody.fromLocation(location, options.bodyPlan, walkGait = options.walkGait, gallopGait = options.gallopGait),
            TridentHitDetector(),
            Cloak(options.cloak),
            SoundsAndParticles(options.sound),
            Mountable(),
            PointDetector(),
            SpiderRenderer(),
        )
    }

    fun createChainVisualizer(location: Location): ECSEntity {
        val segmentPlans = options.bodyPlan.legs.lastOrNull()?.segments ?: throw Error("Cannot find segment plans")

        return ecs.spawn(KinematicChainVisualizer.create(
            segmentPlans = segmentPlans,
            root = location.toVector(),
            world = location.world ?: throw Error("location.world is null"),
            straightenRotation = options.walkGait.legStraightenRotation,
        ).apply {
            detailed = renderDebugVisuals
        })
    }

    fun recreateSpider() {
        val spider = ecs.query<SpiderBody>().firstOrNull() ?: return
        createSpider(spider.location())
    }
}

class MiscellaneousOptions {
    var showLaser = true
}