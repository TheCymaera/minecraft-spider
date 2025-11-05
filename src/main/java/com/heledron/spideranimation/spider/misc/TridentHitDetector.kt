package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.ECSEntity
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import org.bukkit.entity.Trident

class TridentHitEvent(val entity: ECSEntity, val spider: SpiderBody)

class TridentHitDetector() {
    var stunned = false
}


fun setupTridentHitDetector(app: ECS) {
    app.onTick {
        for ((entity, spider, mount, _) in app.query<ECSEntity, SpiderBody, Mountable, TridentHitDetector>()) {
            val tridents = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), 1.5, 1.5, 1.5) {
                it is Trident && it.shooter != mount.getRider()
            }
            for (trident in tridents) {
                if (trident != null && trident.velocity.length() > 2.0) {
                    val tridentDirection = trident.velocity.normalize()

                    trident.velocity = tridentDirection.clone().multiply(-.3)
                    app.emit(TridentHitEvent(
                        entity = entity,
                        spider = spider
                    ))

                    spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))

                    // apply rotational acceleration
                    val hitDirection = spider.position.clone().subtract(trident.location.toVector()).normalize()
                    val axis = UP_VECTOR.crossProduct(tridentDirection)
                    val angle = hitDirection.angle(UP_VECTOR)

                    val accelerationMagnitude = angle * spider.gait.tridentRotationalKnockBack.toFloat()

                    spider.accelerateRotation(axis, accelerationMagnitude)
                }
            }
        }
    }
}