package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.moveTowards
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

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

        val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.moveAcceleration)

        val currentDistance = spider.position.horizontalDistance(target)

        if (currentDistance > distance + decelerateDistance) {
            spider.walkAt(direction.clone().multiply(spider.gait.maxSpeed))
        } else {
            spider.walkAt(Vector(0.0, 0.0, 0.0))
        }
    }
}

class DirectionBehaviour(val spider: Spider, val targetDirection: Vector, val walkDirection: Vector) : SpiderComponent {
    override fun update() {
        spider.rotateTowards(targetDirection)
        spider.walkAt(walkDirection.clone().multiply(spider.gait.maxSpeed))
    }
}



fun Spider.rotateTowards(targetVector: Vector) {
//    val maxAcceleration = moveGait.rotateAcceleration * body.legs.filter { it.isGrounded() }.size / body.legs.size
//    yawVelocity = yawVelocity.moveTowards(.0f, maxAcceleration)
//    pitchVelocity = pitchVelocity.moveTowards(.0f, maxAcceleration)
//    rollVelocity = rollVelocity.moveTowards(.0f, maxAcceleration)

    val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

    val targetEuler = Quaternionf()
        .rotationTo(FORWARD_VECTOR.toVector3f(), targetVector.toVector3f())
        .getEulerAnglesYXZ(Vector3f())

    // clamp pitch
    targetEuler.x = targetEuler.x.coerceIn(preferredPitch - gait.preferredPitchLeeway, preferredPitch + gait.preferredPitchLeeway)

    // clamp roll
    targetEuler.z = preferredRoll

    // clamp yaw if uncomfortable
    if (body.legs.any { it.isUncomfortable && !it.isMoving }) targetEuler.y = currentEuler.y

    // get diff
    val diffEuler = Vector3f(targetEuler).sub(currentEuler)
    if (diffEuler.y > PI) diffEuler.y -= 2 * PI.toFloat()
    if (diffEuler.y < -PI) diffEuler.y += 2 * PI.toFloat()

    isRotatingYaw = (diffEuler.x + diffEuler.y + diffEuler.z) > 0.001f
    diffEuler.lerp(Vector3f(), gait.rotationLerp)


    val diff = Quaternionf().rotationYXZ(diffEuler.y, diffEuler.x, diffEuler.z)

    // convert to premultiplied
    val conjugated = Quaternionf(orientation).mul(diff).mul(Quaternionf(orientation).invert())

    val conjugatedEuler = conjugated.getEulerAnglesYXZ(Vector3f())
    val maxAcceleration = gait.rotateAcceleration * body.legs.filter { it.isGrounded() }.size / body.legs.size
    rotationalVelocity.moveTowards(conjugatedEuler, maxAcceleration)
}

fun Spider.walkAt(targetVelocity: Vector) {
    val acceleration = gait.moveAcceleration// * body.legs.filter { it.isGrounded() }.size / body.legs.size
    val target = targetVelocity.clone()


    if (body.legs.any { it.isUncomfortable && !it.isMoving }) { //  && !it.targetOutsideComfortZone
        val scaled = target.setY(velocity.y).multiply(gait.uncomfortableSpeedMultiplier)
        velocity.moveTowards(scaled, acceleration)
        isWalking = targetVelocity.x != 0.0 && targetVelocity.z != 0.0
    } else {
        velocity.moveTowards(target.setY(velocity.y), acceleration)
        isWalking = velocity.x != 0.0 && velocity.z != 0.0
    }

    if (this.tridentDetector.stunned && targetVelocity.isZero) isWalking = false
}