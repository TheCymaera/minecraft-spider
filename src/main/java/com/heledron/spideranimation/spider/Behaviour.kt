package com.heledron.spideranimation.spider

import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

class StayStillBehaviour(val spider: Spider) : SpiderComponent {
    override fun update() {
        spider.walkAt(Vector(0.0, 0.0, 0.0))
        spider.rotateTowards(spider.location.direction.clone().setY(0.0))
    }
}

class TargetBehaviour(val spider: Spider, val target: Location, val distance: Double) : SpiderComponent {
    override fun update() {
        val direction = target.toVector().clone().subtract(spider.location.toVector()).normalize()
        spider.rotateTowards(direction)

        val currentSpeed = spider.velocity.length()

        val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.walkAcceleration)

        val currentDistance = horizontalDistance(spider.location.toVector(), target.toVector())

        if (currentDistance > distance + decelerateDistance) {
            spider.walkAt(direction.clone().multiply(spider.gait.walkSpeed))
        } else {
            spider.walkAt(Vector(0.0, 0.0, 0.0))
        }
    }
}

class DirectionBehaviour(val spider: Spider, val targetDirection: Vector, val walkDirection: Vector) : SpiderComponent {
    override fun update() {
        spider.rotateTowards(targetDirection)
        spider.walkAt(walkDirection.clone().multiply(spider.gait.walkSpeed))
    }
}



fun Spider.rotateTowards(targetDirection: Vector) {
    val preferredPitch = preferredPitch()
    val clamp = toRadians(1.0)


    // pitch
    val targetPitch = toDegrees(targetDirection.getPitch().coerceIn(preferredPitch - clamp, preferredPitch + clamp)).toFloat()
    val oldPitch = location.pitch
    location.pitch = oldPitch.lerp(targetPitch, .3f)//.coerceIn(minPitch, maxPitch)
    isRotatingPitch = abs(targetPitch - oldPitch) > 0.0001

    // yaw
    val maxSpeed = gait.rotateSpeed * body.legs.filter { it.isGrounded() }.size / body.legs.size
    val oldYaw = toRadians(location.yaw)

    val circle = 2 * PI
    var targetYaw = atan2(-targetDirection.x, targetDirection.z).toFloat()
    targetYaw += (circle * floor((oldYaw - targetYaw + PI) / circle)).toFloat()

    isRotatingYaw = abs(targetYaw - oldYaw) > 0.0001

    yawVelocity = 0f
    if (!isRotatingYaw || body.legs.any { it.isUncomfortable && !it.isMoving }) return

    val newYaw = oldYaw.moveTowards(targetYaw, maxSpeed)
    location.yaw = toDegrees(newYaw)

    yawVelocity = -(newYaw - oldYaw)
}

fun Spider.walkAt(targetVelocity: Vector) {
    val acceleration = gait.walkAcceleration// * body.legs.filter { it.isGrounded() }.size / body.legs.size
    val target = targetVelocity.clone()

    isWalking = true

    if (body.legs.any { it.isUncomfortable && !it.isMoving }) { //  && !it.targetOutsideComfortZone
        val scaled = target.setY(velocity.y).multiply(gait.uncomfortableSpeedMultiplier)
        velocity.moveTowards(scaled, acceleration)
    } else {
        velocity.moveTowards(target.setY(velocity.y), acceleration)
        isWalking = velocity.x != 0.0 && velocity.z != 0.0
    }
}