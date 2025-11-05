package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.ECSEntity
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.position
import org.bukkit.entity.Trident

class TridentHitEvent(val entity: ECSEntity, val spider: SpiderBody, val trident: Trident)

class TridentHitDetector() {
    var stunned = false
}


fun setupTridentHitDetector(app: ECS) {
    app.onTick {
        for ((entity, spider, _) in app.query<ECSEntity, SpiderBody, TridentHitDetector>()) {
            val rider = entity.query<Mountable>()?.getRider()

            val location = spider.position.toLocation(spider.world)
            val tridents = spider.world.getNearbyEntities(location, 1.5, 1.5, 1.5)
                .filterIsInstance<Trident>()

            for (trident in tridents) {
                if (rider !== null && trident.shooter == rider) continue

                if (trident.velocity.length() < 2.0) continue

                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                app.emit(TridentHitEvent(entity = entity, spider = spider, trident = trident))

                spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))

                // apply rotational acceleration
                val hitDirection = spider.position.clone().subtract(trident.position).normalize()
                val axis = UP_VECTOR.crossProduct(tridentDirection)
                val angle = hitDirection.angle(UP_VECTOR)

                val accelerationMagnitude = angle * spider.gait.tridentRotationalKnockBack.toFloat()

                spider.accelerateRotation(axis, accelerationMagnitude)
            }
        }
    }
}