package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.moveTowards
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f


interface SpiderBehaviour : ECSComponent

class StayStillBehaviour() : SpiderBehaviour

class TargetBehaviour(val target: Vector, val distance: Double) : SpiderBehaviour

class DirectionBehaviour(val targetDirection: Vector, val walkDirection: Vector) : SpiderBehaviour

fun setupBehaviours(app: ECS) {
    // Stay still behaviour
    app.onTick {
        for ((entity, spider, options, _) in app.query<ECSEntity, SpiderBody, SpiderOptions, StayStillBehaviour>()) {
            val tridentDetector = entity.query<TridentHitDetector>()
            spider.walkAt(Vector(0.0, 0.0, 0.0), tridentDetector, options.gait)
            spider.rotateTowards(spider.forwardDirection().setY(0.0), options.gait)
        }
    }

    // Target behaviour
    app.onTick {
        for ((entity, spider, options, behaviour) in app.query<ECSEntity, SpiderBody, SpiderOptions, TargetBehaviour>()) {
            val direction = behaviour.target.clone().subtract(spider.position).normalize()
            spider.rotateTowards(direction, options.gait)

            val currentSpeed = spider.velocity.length()

            val decelerateDistance = (currentSpeed * currentSpeed) / (2 * options.gait.moveAcceleration)

            val currentDistance = spider.position.horizontalDistance(behaviour.target)

            val tridentDetector = entity.query<TridentHitDetector>()
            if (currentDistance > behaviour.distance + decelerateDistance) {
                spider.walkAt(direction.clone().multiply(options.gait.maxSpeed), tridentDetector, options.gait)
            } else {
                spider.walkAt(Vector(0.0, 0.0, 0.0), tridentDetector, options.gait)
            }
        }
    }

    // Direction behaviour
    app.onTick {
        for ((entity, spider, options, behaviour) in app.query<ECSEntity, SpiderBody, SpiderOptions, DirectionBehaviour>()) {
            spider.rotateTowards(behaviour.targetDirection, options.gait)


            val tridentDetector = entity.query<TridentHitDetector>()
            spider.walkAt(
                behaviour.walkDirection.clone().multiply(options.gait.maxSpeed),
                tridentDetector,
                options.gait
            )
        }
    }
}



private fun SpiderBody.rotateTowards(targetVector: Vector, gait: Gait) {
    val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

    val targetEuler = Quaternionf()
        .rotationTo(FORWARD_VECTOR.toVector3f(), targetVector.toVector3f())
        .getEulerAnglesYXZ(Vector3f())

    // clamp pitch
    targetEuler.x = targetEuler.x.coerceIn(preferredPitch - gait.preferredPitchLeeway, preferredPitch + gait.preferredPitchLeeway)

    // clamp roll
    targetEuler.z = preferredRoll

    // clamp yaw if uncomfortable
    if (legs.any { it.isUncomfortable && !it.isMoving }) targetEuler.y = currentEuler.y

    val targetOrientation = Quaternionf().rotationYXZ(targetEuler.y, targetEuler.x, targetEuler.z)

    // Soften error the same way Euler.lerp(zero, rotationLerp) did: blend towards current
    val softenedTarget = Quaternionf(orientation).slerp(targetOrientation, 1f - gait.rotationLerp)

    // World-space delta → angular velocity (premultiplied: orientation = Rot(ω) * orientation)
    val desiredDelta = Quaternionf(softenedTarget).mul(Quaternionf(orientation).invert())
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
