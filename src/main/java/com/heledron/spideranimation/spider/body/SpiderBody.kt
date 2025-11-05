package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.LerpGait
import com.heledron.spideranimation.spider.configuration.SpiderDebugOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.deprecated.isOnGround
import com.heledron.spideranimation.utilities.deprecated.raycastGround
import com.heledron.spideranimation.utilities.deprecated.resolveCollision
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.pitch
import com.heledron.spideranimation.utilities.maths.pitchRadians
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.maths.yawRadians
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector2d
import org.joml.Vector3f
import kotlin.math.*


class SpiderBodyHitGroundEvent(val spider: SpiderBody)

class SpiderBody(
    val world: World,
    val position: Vector,
    val orientation: Quaternionf,
    var bodyPlan: BodyPlan,
    var gallopGait: Gait,
    var walkGait: Gait,
) {
    var onGround = false; private set
    var legs: List<Leg> = emptyList()
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector(0.0, 0.0, 0.0); private set

    var debug = SpiderDebugOptions()

    // params
    var gallop = false
    val gait get() = if (gallop) gallopGait else walkGait


    // state
    var isWalking = false
    var isRotatingYaw = false

    fun lerpedGait(): LerpGait {
        if (isRotatingYaw) {
            return gait.moving.clone()
        }

        val speedFraction = velocity.length() / gait.maxSpeed
        return gait.stationary.clone().lerp(gait.moving, speedFraction)
    }

    companion object {
        fun fromLocation(location: Location, bodyPlan: BodyPlan, gallopGait: Gait, walkGait: Gait): SpiderBody {
            val world = location.world!!
            val position = location.toVector()
            val orientation = Quaternionf().rotationYXZ(location.yawRadians(), location.pitchRadians(), 0f)
            return SpiderBody(world, position, orientation, bodyPlan, gallopGait = gallopGait, walkGait = walkGait)
        }
    }

    // utils
    fun location(): Location {
        val location = position.toLocation(world)
        location.direction = forwardDirection()
        return location
    }

    fun forwardDirection() = FORWARD_VECTOR.rotate(Quaterniond(orientation))

    // memo
    var preferredPitch = orientation.getEulerAnglesYXZ(Vector3f()).x
    var preferredRoll = orientation.getEulerAnglesYXZ(Vector3f()).z
    var preferredOrientation = Quaternionf(orientation)

    val velocity = Vector(0.0, 0.0, 0.0)
    val rotationalVelocity = Vector3f(0f,0f,0f)

    fun accelerateRotation(axis: Vector, angle: Float) {
        val acceleration = Quaternionf().rotateAxis(angle, axis.toVector3f())
        val oldVelocity = Quaternionf().rotationYXZ(rotationalVelocity.y, rotationalVelocity.x, rotationalVelocity.z)

        val rotVelocity = acceleration.mul(oldVelocity)

        val rotEuler = rotVelocity.getEulerAnglesYXZ(Vector3f())
        rotationalVelocity.set(rotEuler)
    }

    fun teleport(entity: ECSEntity, newLocation: Location) {
        val diff = newLocation.toVector().subtract(position)

        position.copy(newLocation.toVector())

        val body = entity.query<SpiderBody>() ?: return
        for (leg in body.legs) leg.endEffector.add(diff)
    }

    private fun updatePreferredAngles() {
        val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

        if (gait.disableAdvancedRotation) {
            preferredPitch = .0f
            preferredRoll = .0f
            preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, .0f, .0f)
            return
        }

        fun getPos(leg: Leg): Vector {
//            if (leg.isOutsideTriggerZone) return leg.endEffector
            return leg.groundPosition ?: leg.restPosition
        }

        val frontLeft  = getPos(legs.getOrNull(0) ?: return)
        val frontRight = getPos(legs.getOrNull(1) ?: return)
        val backLeft  = getPos(legs.getOrNull(legs.size - 2) ?: return)
        val backRight = getPos(legs.getOrNull(legs.size - 1) ?: return)

        val forwardLeft = frontLeft.clone().subtract(backLeft)
        val forwardRight = frontRight.clone().subtract(backRight)
        val forward = listOf(forwardLeft, forwardRight).average()

        val sideways = Vector(0.0,0.0,0.0)
        for (i in 0 until legs.size step 2) {
            val left = legs.getOrNull(i) ?: continue
            val right = legs.getOrNull(i + 1) ?: continue

            sideways.add(getPos(right).clone().subtract(getPos(left)))
        }

        preferredPitch = forward.pitch().lerp(preferredPitch, gait.preferredRotationLerpFraction)
        preferredRoll = sideways.pitch().lerp(preferredRoll, gait.preferredRotationLerpFraction)

        if (preferredPitch < gait.preferLevelBreakpoint) preferredPitch *= 1 - gait.preferLevelBias
        if (preferredRoll < gait.preferLevelBreakpoint) preferredRoll *= 1 - gait.preferLevelBias


        preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, preferredPitch, preferredRoll)
    }

    fun init(ecs: ECS, entity: ECSEntity) {
        legs = bodyPlan.legs.map { Leg( ecs, entity, this, it) }
    }

    fun update(ecs: ECS, entity: ECSEntity) {
        if (legs.isEmpty()) {
            init(ecs, entity)

            if (legs.isEmpty()) {
                sendDebugChatMessage("WARNING: No legs")
                return
            }
        }

        updatePreferredAngles()

        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / legs.size

        // apply gravity and air resistance
        velocity.y -= gait.gravityAcceleration
        velocity.y *= (1 - gait.airDragCoefficient)

        // apply rotational velocity
        val rotVelocity = Quaternionf().rotationYXZ(rotationalVelocity.y, rotationalVelocity.x, rotationalVelocity.z)
        orientation.set(rotVelocity.mul(orientation))

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

        val normal = calcNormal()
        this.normal = normal

        normalAcceleration = Vector(0.0, 0.0, 0.0)
        if (normal != null) {
            val preferredY = calcPreferredY()
            val preferredYAcceleration = (preferredY - position.y - velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = normal.normal.clone().multiply(accelerationMagnitude)

            // if the horizontal acceleration is too high,
            // there's no point accelerating as the spider will fall over anyway
            if (normalAcceleration.horizontalLength() > normalAcceleration.y) normalAcceleration.multiply(0.0)

            velocity.add(normalAcceleration)
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

        updatePreferredAngles()
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
    }


    private fun calcPreferredY(): Double {
        val lookAhead = position.clone().add(velocity)
        val ground = world.raycastGround(lookAhead, DOWN_VECTOR.rotate(preferredOrientation), lerpedGait().bodyHeight)
        val groundY = ground?.hitPosition?.y ?: -Double.MAX_VALUE

        val averageY = legs.map { it.target.position.y }.average() + lerpedGait().bodyHeight

        val pivot = gait.legChainPivotMode.get(this)
        val target = UP_VECTOR.rotate(pivot).multiply(gait.maxBodyDistanceFromGround)
        val targetY = max(averageY, groundY + target.y)
        val stabilizedY = position.y.lerp(targetY, gait.bodyHeightCorrectionFactor)

        return stabilizedY
    }

    private fun applyStabilization(normal: NormalInfo) {
        if (normal.origin == null) return
        if (normal.centreOfMass == null) return

        if (normal.origin.horizontalDistance(normal.centreOfMass) < gait.polygonLeeway) {
            normal.origin.x = normal.centreOfMass.x
            normal.origin.z = normal.centreOfMass.z
        }

        val stabilizationTarget = normal.origin.clone().setY(normal.centreOfMass.y)
        normal.centreOfMass.lerp(stabilizationTarget, gait.stabilizationFactor)

        normal.normal.copy(normal.centreOfMass).subtract(normal.origin).normalize()
    }

    private fun calcLegacyNormal(): NormalInfo? {
        val pairs = LegLookUp.diagonalPairs(legs.indices.toList())
        if (pairs.any { pair -> pair.mapNotNull { legs.getOrNull(it) }.all { it.isGrounded() } }) {
            return NormalInfo(normal = Vector(0, 1, 0))
        }

        return null
    }

    private fun calcNormal(): NormalInfo? {
        if (gait.useLegacyNormalForce) return calcLegacyNormal()

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
            ).apply { applyStabilization(this) }
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
        ).apply { applyStabilization(this)}
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
        for ((entity, spider) in app.query<ECSEntity, SpiderBody>()) {
            spider.update(app, entity)
        }
    }
}