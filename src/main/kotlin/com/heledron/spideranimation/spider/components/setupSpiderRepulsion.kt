package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.horizontalLength

fun setupSpiderRepulsion(app: ECS) {
    app.onTick {
        val spiders = app.query<SpiderBody, SpiderOptions, Locomotion>().toList()

        for (i in spiders.indices) {
            for (j in i + 1 until spiders.size) {
                val (bodyA, optionsA, locomotionA) = spiders[i]
                val (bodyB, optionsB, locomotionB) = spiders[j]

                if (bodyA.world !== bodyB.world) continue

                val delta = bodyB.position.clone().subtract(bodyA.position)
                val distance = delta.length()

                val separation = separationDistance(optionsA, optionsB)
                if (separation <= 0.0 || distance >= separation) continue

                val away = delta.setY(0.0)
                if (away.lengthSquared() < 1e-8) {
                    // same column
                    away.setX(if (bodyA.uuid < bodyB.uuid) 1.0 else -1.0)
                }
                away.normalize()

                val overlap = 1.0 - distance / separation
                locomotionA.push(away.clone().multiply(-pushSpeed(optionsA, overlap)))
                locomotionB.push(away.clone().multiply(pushSpeed(optionsB, overlap)))
            }
        }
    }
}

private const val REPULSION_STRENGTH = 3.0
private const val SEPARATION_FACTOR = 1.2

private fun pushSpeed(options: SpiderOptions, overlap: Double): Double {
    return options.gait.maxSpeed * overlap * REPULSION_STRENGTH
}

private fun footprintRadius(options: SpiderOptions): Double {
    return options.bodyPlan.legs.maxOfOrNull { it.restPosition.horizontalLength() } ?: 0.0
}

private fun separationDistance(a: SpiderOptions, b: SpiderOptions): Double {
    return (footprintRadius(a) + footprintRadius(b)) * SEPARATION_FACTOR
}
