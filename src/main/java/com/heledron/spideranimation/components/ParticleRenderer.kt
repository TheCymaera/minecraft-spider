package com.heledron.spideranimation.components

import com.heledron.spideranimation.Spider
import com.heledron.spideranimation.SpiderComponent
import com.heledron.spideranimation.spawnParticle
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector


class ParticleRenderer(val spider: Spider) : SpiderComponent {
    override fun render() {
        renderSpider(spider)
    }

    companion object {
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

        fun renderLeg(leg: Leg) {
            val world = leg.spider.location.world!!
            var current = leg.chain.root.toLocation(world)

            for ((i, segment) in leg.chain.segments.withIndex()) {
                val thickness = (leg.chain.segments.size - i - 1) * 0.025
                renderLine(current, segment.position, thickness)
                current = segment.position.toLocation(world)
            }
        }

        fun renderTarget(location: Location) {
            spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
        }

        fun renderSpider(spider: Spider) {
            for (leg in spider.body.legs) {
                renderLeg(leg)
            }
        }
    }
}