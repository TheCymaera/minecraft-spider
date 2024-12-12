package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.EventEmitter
import com.heledron.spideranimation.utilities.UP_VECTOR
import com.heledron.spideranimation.utilities.runLater
import org.bukkit.entity.Trident
import org.joml.Quaternionf
import org.joml.Vector3f

class TridentHitDetector(val spider: Spider): SpiderComponent {
    val onHit = EventEmitter()
    var stunned = false

    init {
        onHit.listen {
//            stunned = true
            runLater(2) { stunned = false }
        }
    }

    override fun update() {
        val tridents = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), 1.5, 1.5, 1.5) {
            it is Trident && it.shooter != spider.mount.getRider()
        }
        for (trident in tridents) {
            if (trident != null && trident.velocity.length() > 2.0) {
                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                onHit.emit()

                spider.velocity.add(tridentDirection.multiply(spider.moveGait.tridentKnockBack))

                // apply rotational acceleration
                val axis = UP_VECTOR.crossProduct(tridentDirection)
                val angle = tridentDirection.angle(UP_VECTOR)

                val accelerationMagnitude = angle * spider.moveGait.tridentKnockBack / 2

                val acceleration = Quaternionf().rotateAxis(accelerationMagnitude.toFloat(), axis.toVector3f())
                val oldVelocity = Quaternionf().rotationYXZ(spider.yawVelocity, spider.pitchVelocity, spider.rollVelocity)

                val rotVelocity = acceleration.mul(oldVelocity)

                val rotEuler = rotVelocity.getEulerAnglesYXZ(Vector3f())
                spider.yawVelocity = rotEuler.y
                spider.pitchVelocity = rotEuler.x
                spider.rollVelocity = rotEuler.z
            }
        }
    }
}