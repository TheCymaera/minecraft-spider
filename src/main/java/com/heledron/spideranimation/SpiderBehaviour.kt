package com.heledron.spideranimation

import org.bukkit.Location
import org.bukkit.util.Vector

interface SpiderBehaviour {
    fun update(spider: Spider)
}


object StayStillBehaviour : SpiderBehaviour {
    override fun update(spider: Spider) {
        spider.accelerateToVelocity(Vector(0.0, 0.0, 0.0))
    }
}

class TargetBehaviour(val target: Location, val distance: Double, val visible: Boolean) : SpiderBehaviour {
    override fun update(spider: Spider) {
        val targetDirection = target.toVector().clone().subtract(spider.location.toVector()).normalize()
        spider.rotateTowards(targetDirection)

        val currentSpeed = spider.velocity.length()

        val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.walkAcceleration)

        val currentDistance = horizontalDistance(spider.location, target)

        if (currentDistance > distance + decelerateDistance) {
            spider.accelerateToVelocity(targetDirection.multiply(spider.gait.walkSpeed))
        } else {
            spider.accelerateToVelocity(Vector(0.0, 0.0, 0.0))
        }
    }
}

class DirectionBehaviour(val direction: Vector) : SpiderBehaviour {
    override fun update(spider: Spider) {
        spider.rotateTowards(direction)
        spider.accelerateToVelocity(direction.clone().multiply(spider.gait.walkSpeed))
    }
}