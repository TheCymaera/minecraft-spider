package com.heledron.spideranimation

import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.components.Mountable
import com.heledron.spideranimation.spider.components.PointDetector
import com.heledron.spideranimation.spider.components.SoundsAndParticles
import com.heledron.spideranimation.spider.components.TridentHitDetector
import com.heledron.spideranimation.spider.presets.hexBot
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import org.bukkit.Location
import org.bukkit.entity.Player

object AppState {
    var miscOptions = MiscellaneousOptions()
    var renderDebugVisuals = false

    val ecs = ECS()

    var target: Location? = null

    fun createSpider(location: Location, options: SpiderOptions): ECSEntity {
        location.y += options.walkGait.stationary.bodyHeight
        return ecs.spawn(
            SpiderBody.fromLocation(location, options.bodyPlan, walkGait = options.walkGait, gallopGait = options.gallopGait, gallop = false),
            TridentHitDetector(),
            Cloak(options.cloak),
            SoundsAndParticles(options.sound),
            Mountable(),
            PointDetector(),
            SpiderRenderer(),
        )
    }

    fun findSpiderByUUID(uuid: java.util.UUID): Pair<ECSEntity, SpiderBody>? {
        return ecs.query<ECSEntity, SpiderBody>().find { it.second.uuid == uuid }
    }

    fun findNearestSpider(player: Player): Pair<ECSEntity, SpiderBody>? {
        return findNearestSpider(player.location)
    }

    fun findNearestSpider(location: Location): Pair<ECSEntity, SpiderBody>? {
        return ecs.query<ECSEntity, SpiderBody>()
            .filter { it.second.world == location.world }
            .minByOrNull { it.second.position.distanceSquared(location.toVector()) }
    }

    fun createChainVisualizer(location: Location, bodyPlan: BodyPlan = hexBot(4, 1.0).bodyPlan): ECSEntity {
        val segmentPlans = bodyPlan.legs.lastOrNull()?.segments ?: throw Error("Cannot find segment plans")

        return ecs.spawn(KinematicChainVisualizer.create(
            segmentPlans = segmentPlans,
            root = location.toVector(),
            world = location.world ?: throw Error("location.world is null"),
            straightenRotation = 0f,
        ).apply {
            detailed = renderDebugVisuals
        })
    }
}

class MiscellaneousOptions {
    var showLaser = true
}