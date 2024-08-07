package com.heledron.spideranimation.spider

import com.heledron.spideranimation.horizontalDistance
import org.bukkit.Location
import org.bukkit.util.Vector

class StayStillBehaviour(val spider: Spider) : SpiderComponent {
    override fun update() {
        spider.walkAt(Vector(0.0, 0.0, 0.0))
        spider.rotateTowards(spider.location.direction.clone().setY(0.0))
    }
}

class TargetBehaviour(val spider: Spider, val target: Location, val distance: Double) : SpiderComponent {
    override fun update() {
        val targetDirection = target.toVector().clone().subtract(spider.location.toVector()).normalize()
        spider.rotateTowards(targetDirection)

        val currentSpeed = spider.velocity.length()

        val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.walkAcceleration)

        val currentDistance = horizontalDistance(spider.location.toVector(), target.toVector())

        if (currentDistance > distance + decelerateDistance) {
            spider.walkAt(targetDirection.multiply(spider.gait.walkSpeed))
        } else {
            spider.walkAt(Vector(0.0, 0.0, 0.0))
        }
    }
}

class DirectionBehaviour(val spider: Spider, val rotateDirection: Vector, val walkDirection: Vector) : SpiderComponent {
    override fun update() {
        spider.rotateTowards(rotateDirection)
        spider.walkAt(walkDirection.clone().multiply(spider.gait.walkSpeed))
    }
}