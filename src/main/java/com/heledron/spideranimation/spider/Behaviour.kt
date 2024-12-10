package com.heledron.spideranimation.spider

import com.heledron.spideranimation.utilities.*
import org.bukkit.util.Vector
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

class StayStillBehaviour(val spider: Spider) : SpiderComponent {
    override fun update() {
        spider.walkAt(Vector(0.0, 0.0, 0.0))
        spider.rotateTowards(spider.forwardDirection().setY(0.0))
    }
}

class TargetBehaviour(val spider: Spider, val target: Vector, val distance: Double) : SpiderComponent {
    override fun update() {
        val direction = target.clone().subtract(spider.position).normalize()
        spider.rotateTowards(direction)

        val currentSpeed = spider.velocity.length()

        val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.walkAcceleration)

        val currentDistance = horizontalDistance(spider.position, target)

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



fun Spider.rotateTowards(target: Vector) =
    rotateTowards(Quaterniond().rotationTo(Vector3d(0.0, 0.0, 1.0), target.toVector3d()))


fun Spider.rotateTowards(target: Quaterniond) {
    // get current
    val euler = this.orientation.getEulerAnglesYXZ(Vector3d())
    val oldPitch = euler.x
    val oldYaw = euler.y

    // get target
    val targetEuler = target.getEulerAnglesYXZ(Vector3d())
    var targetPitch = targetEuler.x
    var targetYaw = targetEuler.y

    // clamp pitch
    val clamp = toRadians(10.0)
    targetPitch = targetPitch.coerceIn(preferredPitch - clamp, preferredPitch + clamp)

    // optimize yaw
    val circle = 2 * PI
    targetYaw += (circle * floor((oldYaw - targetYaw + PI) / circle)).toFloat()

    // get yaw change speed
    var maxYawChange = gait.rotateSpeed * body.legs.filter { it.isGrounded() }.size / body.legs.size
    if (body.legs.any { it.isUncomfortable && !it.isMoving }) maxYawChange = .0

    // apply
    euler.x = oldPitch.lerp(targetPitch, .3)
    euler.y = oldYaw.moveTowards(targetYaw, maxYawChange)
    euler.z = euler.z.lerp(preferredRoll, .1)

    yawVelocity = -(euler.y - oldYaw)
    isRotatingPitch = abs(targetPitch - oldPitch) > 0.0001
    isRotatingYaw = abs(targetYaw - oldYaw) > 0.0001

    orientation.rotationYXZ(euler.y, euler.x, euler.z)
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