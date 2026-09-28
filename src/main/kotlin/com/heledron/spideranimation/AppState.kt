package com.heledron.spideranimation

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

    fun createSpider(location: Location, options: SpiderOptions): ECSEntity {
        location.y += options.walkGait.stationary.bodyHeight
        return ecs.spawn(
            SpiderBody.fromLocation(location),
            options,
            TridentHitDetector(),
            Cloak(),
            SoundsAndParticles(),
            Mountable(),
            PointDetector(),
            SpiderRenderer(),
        )
    }

    fun findSpiderByUUID(uuid: java.util.UUID): ECSEntity? {
        return ecs.query<ECSEntity, SpiderBody>().find { it.second.uuid == uuid }?.first
    }

    fun findNearestSpider(player: Player): ECSEntity? {
        return findNearestSpider(player.location)
    }

    fun findNearestSpider(location: Location): ECSEntity? {
        return ecs.query<ECSEntity, SpiderBody>()
            .filter { it.second.world == location.world }
            .minByOrNull { it.second.position.distanceSquared(location.toVector()) }
            ?.first
    }
}

class MiscellaneousOptions {
    var showLaser = true
}