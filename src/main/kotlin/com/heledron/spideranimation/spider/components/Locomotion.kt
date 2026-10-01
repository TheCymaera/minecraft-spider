package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.getYXZRelative
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.moveTowards
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f


class Locomotion : ECSComponent {
    var walkVelocity: Vector? = null; private set
    var faceDirection: Vector? = null; private set
    var pushVelocity: Vector? = null; private set

    private var walkPriority = Int.MIN_VALUE
    private var facePriority = Int.MIN_VALUE

    fun walk(velocity: Vector, priority: Int = DEFAULT_PRIORITY) {
        if (priority < walkPriority) return
        walkPriority = priority
        walkVelocity = velocity
    }

    fun face(direction: Vector, priority: Int = DEFAULT_PRIORITY) {
        if (priority < facePriority) return
        facePriority = priority
        faceDirection = direction
    }

    fun push(velocity: Vector) {
        val accumulated = pushVelocity
        if (accumulated == null) pushVelocity = velocity.clone()
        else accumulated.add(velocity)
    }

    fun clear() {
        walkVelocity = null
        faceDirection = null
        pushVelocity = null
        walkPriority = Int.MIN_VALUE
        facePriority = Int.MIN_VALUE
    }

    companion object {
        const val DEFAULT_PRIORITY = 0
        const val LASER_PRIORITY = 10
        const val RIDER_PRIORITY = 20
    }
}

fun setupLocomotion(app: ECS) {
    app.onPostTick {
        for ((entity, spider, options, locomotion) in app.query<ECSEntity, SpiderBody, SpiderOptions, Locomotion>()) {
            val tridentDetector = entity.query<TridentHitDetector>()

            val targetVelocity = Vector(0.0, 0.0, 0.0)
            locomotion.walkVelocity?.let { targetVelocity.add(it) }
            locomotion.pushVelocity?.let { targetVelocity.add(it) }

            spider.walkAt(targetVelocity, tridentDetector, options.gait)

            spider.rotateTowards(locomotion.faceDirection ?: spider.forwardDirection().setY(0.0), options.gait)

            locomotion.clear()
        }
    }
}


private fun SpiderBody.rotateTowards(targetVector: Vector, gait: Gait) {
    val facing = Quaternionf().rotationTo(FORWARD_VECTOR.toVector3f(), targetVector.toVector3f())

    val relativeEuler = facing.getYXZRelative(preferredOrientation)

    // clamp pitch
    relativeEuler.x = relativeEuler.x.coerceIn(-gait.preferredPitchLeeway, gait.preferredPitchLeeway)

    // clamp roll
    relativeEuler.z = 0f

    // clamp yaw if uncomfortable
    if (legs.any { it.isUncomfortable && !it.isMoving }) relativeEuler.y = 0f

    val targetOrientation = Quaternionf(preferredOrientation)
        .mul(Quaternionf().rotationYXZ(relativeEuler.y, relativeEuler.x, relativeEuler.z))

    // smooth target
    val easedTarget = Quaternionf(orientation).slerp(targetOrientation, 1f - gait.rotationLerp)

    // world-space delta → angular velocity (premultiplied: orientation = Rot(ω) * orientation)
    val desiredDelta = Quaternionf(easedTarget).mul(Quaternionf(orientation).invert())
    val axisAngle = AxisAngle4f().set(desiredDelta)
    val desiredOmega = if (axisAngle.angle < 1e-8f) {
        Vector3f()
    } else {
        Vector3f(axisAngle.x, axisAngle.y, axisAngle.z).mul(axisAngle.angle)
    }

    isRotatingYaw = desiredOmega.lengthSquared() > 0.001f * 0.001f

    val maxAcceleration = gait.rotateAcceleration * legs.filter { it.isGrounded() }.size / legs.size
    rotationalVelocity.moveTowards(desiredOmega, maxAcceleration)
}

private fun SpiderBody.walkAt(targetVelocity: Vector, tridentDetector: TridentHitDetector?, gait: Gait) {
    val acceleration = gait.moveAcceleration// * body.legs.filter { it.isGrounded() }.size / body.legs.size
    val target = targetVelocity.clone()

    if (legs.any { it.isUncomfortable && !it.isMoving }) { //  && !it.targetOutsideComfortZone
        val scaled = target.setY(velocity.y).multiply(gait.uncomfortableSpeedMultiplier)
        velocity.moveTowards(scaled, acceleration)
        isWalking = targetVelocity.x != 0.0 && targetVelocity.z != 0.0
    } else {
        velocity.moveTowards(target.setY(velocity.y), acceleration)
        isWalking = velocity.x != 0.0 && velocity.z != 0.0
    }

    if (tridentDetector != null && tridentDetector.stunned && targetVelocity.isZero) isWalking = false
}
