package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.MultiModelRenderer
import com.heledron.spideranimation.utilities.spawnParticle
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector


class SpiderRenderer(val spider: Spider): SpiderComponent {
    private val renderer = MultiModelRenderer()

    val bodyModel = BodyModels.FLAT.map { it.clone().scale(spider.options.bodyPlan.scale.toFloat()) }

    override fun render() {
        renderer.render("spider", spiderModel(spider, bodyModel))
        if (spider.showDebugVisuals) renderer.render("debug", spiderDebugModel(spider))
        renderer.flush()
    }

    override fun close() {
        renderer.close()
    }
}

class SpiderParticleRenderer(val spider: Spider): SpiderComponent {
    override fun render() {
        renderSpider(spider)
    }

    companion object {
        fun renderTarget(location: Location) {
            spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
        }

        fun renderSpider(spider: Spider) {
            for (leg in spider.body.legs) {
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
                spawnParticle(Particle.BUBBLE, current, 1, thickness, thickness, thickness, 0.0)
                current.add(step)
            }
        }
    }
}