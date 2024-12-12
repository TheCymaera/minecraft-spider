package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.MultiModelRenderer
import com.heledron.spideranimation.utilities.SeriesScheduler
import com.heledron.spideranimation.utilities.interval
import com.heledron.spideranimation.utilities.spawnParticle
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import kotlin.random.Random

class SpiderRenderer(val spider: Spider): SpiderComponent {
    private val renderer = MultiModelRenderer()

    // apply eye blinking effect
    val eyeInterval = interval(0,10) {
        val pieces = spider.options.bodyPlan.bodyModel.pieces.filter { it.tags.contains("eye") }

        if (Random.nextBoolean()) return@interval
        for (piece in pieces) piece.block = spider.options.bodyPlan.eyePalette.random()
    }

    // apply blinking lights effect
    val blinkingInterval = interval(0,5) {
        val pieces = spider.options.bodyPlan.bodyModel.pieces.filter { it.tags.contains("blinking_lights") }

        if (Random.nextBoolean()) return@interval
        for (piece in pieces) {
            val palette = spider.options.bodyPlan.blinkingPalette.random()
            piece.block = palette.first
            piece.brightness = palette.second
        }
    }
    /*interval(0,20 * 4) {
        val pieces = spider.options.bodyPlan.bodyModel.pieces.filter { it.tags.contains("blinking_lights") }

//        val blinkBlock = spider.options.bodyPlan.blinkingPalette.random()
        for (piece in pieces) {
            val currentBlock = piece.block
            val currentBrightness = piece.brightness

            val scheduler = SeriesScheduler()
            for (i in 0 until 2) {
                scheduler.run {
                    piece.block = spider.options.bodyPlan.blinkingPalette.random()
                    piece.brightness = Display.Brightness(15, 15)
                }
                scheduler.sleep(2)
                scheduler.run {
                    piece.block = currentBlock
                    piece.brightness = currentBrightness
                }
                scheduler.sleep(2)
            }

        }
    }*/

    override fun render() {
        renderer.render("spider", spiderModel(spider))
        if (spider.showDebugVisuals) renderer.render("debug", spiderDebugModel(spider))
        renderer.flush()
    }

    override fun close() {
        renderer.close()
        eyeInterval.close()
        blinkingInterval.close()
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



