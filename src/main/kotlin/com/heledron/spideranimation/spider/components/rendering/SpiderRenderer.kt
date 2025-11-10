package com.heledron.spideranimation.spider.components.rendering

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.components.PointDetector
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.events.interval
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.random.Random

class SpiderRenderer {
    var renderDebugVisuals = false
    var useParticles = false
}

fun setupRenderer(app: ECS) {
    // apply eye blinking effect
    interval(0,10) {
        for (spider in app.query<SpiderBody>()) {
            val pieces = spider.bodyPlan.bodyModel.pieces.filter { it.tags.contains("eye") }

            if (Random.nextBoolean()) return@interval
            for (piece in pieces) {
                val block = spider.bodyPlan.eyePalette.random()
                piece.block = block.first
                piece.brightness = block.second
            }
        }
    }

    // apply blinking lights effect
    interval(0,5) {
        for (spider in app.query<SpiderBody>()) {
            val pieces = spider.bodyPlan.bodyModel.pieces.filter { it.tags.contains("blinking_lights") }

            if (Random.nextBoolean()) return@interval
            for (piece in pieces) {
                val block = spider.bodyPlan.blinkingPalette.random()
                piece.block = block.first
                piece.brightness = block.second
            }
        }
    }

    app.onRender {
        for ((spider, cloak, pointDetector, renderer) in app.query<SpiderBody, Cloak, PointDetector, SpiderRenderer>()) {
            if (renderer.useParticles) {
                SpiderParticleRenderer.renderSpider(spider)
            } else {
                renderSpider(spider, cloak).submit(spider)
            }


            if (renderer.renderDebugVisuals) spiderDebugRenderEntities(spider, pointDetector).submit(spider to "debug")
        }
    }
}

private object SpiderParticleRenderer {
//    fun renderTarget(location: Location) {
//        location.world?.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
//    }

    fun renderSpider(spider: SpiderBody) {
        for (leg in spider.legs) {
            val world = leg.spider.world
            val chain = leg.chain
            var current = chain.root.toLocation(world)

            for ((i, segment) in chain.segments.withIndex()) {
                val thickness = (chain.segments.size - i - 1) * 0.025
                renderLine(current, segment.position, thickness)
                current = segment.position.toLocation(world)
            }
        }
    }

    fun renderLine(point1: Location, point2: Vector, thickness: Double) {
        val gap = .05

        val amount = point1.toVector().distance(point2) / gap
        val step = point2.clone().subtract(point1.toVector()).multiply(1 / amount)

        val current = point1.clone()

        for (i in 0..amount.toInt()) {
            point1.world?.spawnParticle(Particle.BUBBLE, current, 1, thickness, thickness, thickness, 0.0)
            current.add(step)
        }
    }
}



