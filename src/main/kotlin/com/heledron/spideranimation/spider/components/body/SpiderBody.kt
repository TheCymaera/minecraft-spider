package com.heledron.spideranimation.spider.components.body

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.Posture
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.isOnGround
import com.heledron.spideranimation.utilities.raycastGround
import com.heledron.spideranimation.utilities.resolveCollision
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.horizontal
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.pitch
import com.heledron.spideranimation.utilities.maths.pitchRadians
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.maths.yawRadians
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector2d
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.*


class SpiderBodyHitGroundEvent(val spider: SpiderBody)

class SpiderBody(
    val uuid: UUID,
    val world: World,
    val position: Vector,
    val orientation: Quaternionf,
) : ECSComponent {
    var onGround = false; private set
    var legs: List<Leg> = emptyList()
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector(0.0, 0.0, 0.0); private set

    // state
    var isWalking = false
    var isRotatingYaw = false

    private var lastAppliedBodyPlan: BodyPlan? = null

    fun updateBodyPlan() {
        lastAppliedBodyPlan = null
    }

    fun posture(gait: Gait): Posture {
        if (isRotatingYaw) {
            return gait.moving.clone()
        }

        val speedFraction = velocity.length() / gait.maxSpeed
        return gait.stationary.clone().lerp(gait.moving, speedFraction)
    }

    companion object {
        fun fromLocation(location: Location, uuid: UUID = UUID.randomUUID()): SpiderBody {
            val world = location.world!!
            val position = location.toVector()
            val orientation = Quaternionf().rotationYXZ(location.yawRadians(), location.pitchRadians(), 0f)
            return SpiderBody(uuid, world, position, orientation)
        }
    }

    // utils
    fun location(): Location {
        val location = position.toLocation(world)
        location.direction = forwardDirection()
        return location
    }

    fun forwardDirection() = FORWARD_VECTOR.rotate(orientation)

    // memo
    var preferredPitch = orientation.getEulerAnglesYXZ(Vector3f()).x
    var preferredRoll = orientation.getEulerAnglesYXZ(Vector3f()).z
    var preferredOrientation = Quaternionf(orientation)

    val velocity = Vector(0.0, 0.0, 0.0)
    /** World-space angular velocity (axis × rate, rad/tick). */
    val rotationalVelocity = Vector3f()

    fun accelerateRotation(axis: Vector, angle: Float) {
        val a = axis.toVector3f()
        if (a.lengthSquared() < 1e-12f) return
        a.normalize().mul(angle)
        rotationalVelocity.add(a)
    }

    fun teleport(entity: ECSEntity, newPosition: Vector) {
        val diff = newPosition.subtract(position)

        position.copy(newPosition)

        val body = entity.query<SpiderBody>() ?: return
        for (leg in body.legs) leg.endEffector.add(diff)
    }

    private fun updatePreferredAngles(gait: Gait) {
        val heading = orientation.horizontal()

        fun getPos(leg: Leg): Vector {
//            if (leg.isOutsideTriggerZone) return leg.endEffector
            return leg.groundTarget?.position ?: leg.restPosition
        }

        val frontLeft  = getPos(legs.getOrNull(0) ?: return)
        val frontRight = getPos(legs.getOrNull(1) ?: return)
        val backLeft  = getPos(legs.getOrNull(legs.size - 2) ?: return)
        val backRight = getPos(legs.getOrNull(legs.size - 1) ?: return)

        val forwardLeft = frontLeft.clone().subtract(backLeft)
        val forwardRight = frontRight.clone().subtract(backRight)
        val forward = listOf(forwardLeft, forwardRight).average()

        val sideways = Vector(0.0,0.0,0.0)
        for (i in legs.indices step 2) {
            val left = legs.getOrNull(i) ?: continue
            val right = legs.getOrNull(i + 1) ?: continue

            sideways.add(getPos(right).clone().subtract(getPos(left)))
        }

        preferredPitch = forward.pitch().lerp(preferredPitch, gait.preferredRotationLerpFraction)
        preferredRoll = sideways.pitch().lerp(preferredRoll, gait.preferredRotationLerpFraction)

        preferredOrientation = Quaternionf(heading).rotateX(preferredPitch).rotateZ(preferredRoll)
    }

    fun update(ecs: ECS, entity: ECSEntity, options: SpiderOptions) {
        val gait = options.gait

        if (lastAppliedBodyPlan !== options.bodyPlan) {
            legs = options.bodyPlan.legs.map { Leg(ecs, entity, this, it, options) }
            lastAppliedBodyPlan = options.bodyPlan
        }

        updatePreferredAngles(gait)

        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / legs.size

        // apply gravity and air resistance
        velocity.y -= gait.gravityAcceleration
        velocity.y *= (1 - gait.airDragCoefficient)

        // apply rotational velocity
        val angularSpeed = rotationalVelocity.length()
        if (angularSpeed > 1e-8f) {
            orientation.premul(
                Quaternionf().rotateAxis(
                    angularSpeed,
                    rotationalVelocity.x / angularSpeed,
                    rotationalVelocity.y / angularSpeed,
                    rotationalVelocity.z / angularSpeed,
                )
            )
        }

        // apply drag while leg on ground
        if (!isWalking) {
            val legDrag = 1 - gait.groundDragCoefficient * fractionOfLegsGrounded
            velocity.x *= legDrag
            velocity.z *= legDrag
        }

        // apply rotational drag
        val rotDrag = 1 - gait.rotationalDragCoefficient * fractionOfLegsGrounded.toFloat()
        rotationalVelocity.mul(rotDrag)

        // apply drag while body on ground
        if (onGround) {
            val bodyDrag = .5f
            velocity.x *= bodyDrag
            velocity.z *= bodyDrag

            rotationalVelocity.mul(bodyDrag)
        }

        val normal = calcNormal(gait)
        this.normal = normal

        normalAcceleration = Vector(0.0, 0.0, 0.0)
        if (normal != null) {
            val preferredPosition = calcPreferredPosition(gait)

            // normal: push towards the preferred height
            val preferredYAcceleration = (preferredPosition.y - position.y - velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = normal.normal.clone().multiply(accelerationMagnitude)

            // if the horizontal acceleration is too high,
            // there's no point accelerating as the spider will fall over anyway
            if (normalAcceleration.horizontalLength() > normalAcceleration.y) normalAcceleration.multiply(0.0)

            velocity.add(normalAcceleration)

            // grip: pull towards the preferred position
            val n = normal.normal.clone().normalize()

            val offset = preferredPosition.clone().subtract(position)
            val gripCorrection = offset.subtract(velocity)

            // the component along the surface normal is already handled by the normal force
            gripCorrection.subtract(n.clone().multiply(gripCorrection.dot(n)))

            val gripLimit = gait.gripStrength * fractionOfLegsGrounded
            if (gripCorrection.lengthSquared() > gripLimit * gripLimit) {
                gripCorrection.normalize().multiply(gripLimit)
            }

            normalAcceleration.add(gripCorrection)
            velocity.add(gripCorrection)
        }

        // apply velocity
        position.add(velocity)

        // resolve collision

        val collision = world.resolveCollision(position, Vector(0.0, min(-1.0, -abs(velocity.y)), 0.0))
        if (collision != null) {
            onGround = true

            val didHit = collision.offset.length() > (gait.gravityAcceleration * 2) * (1 - gait.airDragCoefficient)
            if (didHit) ecs.emit(SpiderBodyHitGroundEvent(spider = this))

            position.y = collision.position.y
            if (velocity.y < 0) velocity.y *= -gait.bounceFactor
            if (velocity.y < gait.gravityAcceleration) velocity.y = .0
        } else {
            onGround = world.isOnGround(position, DOWN_VECTOR.rotate(orientation))
        }

        val updateOrder = gait.type.getLegsInUpdateOrder(this)
        for (leg in updateOrder) leg.updateMemo()
        for (leg in updateOrder) leg.update()

        updatePreferredAngles(gait)
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
    }


    private fun calcPreferredPosition(gait: Gait): Vector {
        val up = UP_VECTOR.rotate(preferredOrientation)
        val down = up.clone().multiply(-1.0)

        val lookAhead = position.clone().add(velocity)
        val ground = world.raycastGround(lookAhead, down, posture(gait).bodyHeight)

        val groundPosition = ground?.hitPosition
            ?: legs.map { it.target.position }.average()

        val target = groundPosition.add(up.multiply(posture(gait).bodyHeight))

        return position.clone().lerp(target, gait.bodyHeightCorrectionFactor)
    }

    private fun calcNormal(gait: Gait): NormalInfo? {
        val centreOfMass = legs.map { it.endEffector }.average()
        centreOfMass.lerp(position, 0.5)
        centreOfMass.y += 0.01

        val groundedLegs = legsInPolygonalOrder().map { legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        val legsPolygon = groundedLegs.map { it.endEffector.clone() }
        val polygonCenterY = legsPolygon.map { it.y }.average()

        // only 1 leg on ground
        if (legsPolygon.size == 1) {
            val origin = groundedLegs.first().endEffector.clone()
            return NormalInfo(
                normal = centreOfMass.clone().subtract(origin).normalize(),
                origin = origin,
                centreOfMass = centreOfMass,
                contactPolygon = legsPolygon
            )
        }

        val polygon2D = legsPolygon.map { Vector2d(it.x, it.z) }

        // inside polygon. accelerate upwards towards centre of mass
        if (pointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)) return NormalInfo(
            normal = Vector(0, 1, 0),
            origin = Vector(centreOfMass.x, polygonCenterY, centreOfMass.z),
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon
        )

        // outside polygon, accelerate at an angle from within the polygon
        val point = nearestPointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)
        val origin = Vector(point.x, polygonCenterY, point.y)
        return NormalInfo(
            normal = centreOfMass.clone().subtract(origin).normalize(),
            origin = origin,
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon
        )
    }
}

class NormalInfo(
    // most of these fields are only used for debug rendering
    val normal: Vector,
    val origin: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null
)

fun setupSpiderBody(app: ECS) {
    app.onTick {
        for ((entity, spider, options) in app.query<ECSEntity, SpiderBody, SpiderOptions>()) {
            spider.update(app, entity, options)
        }
    }
}