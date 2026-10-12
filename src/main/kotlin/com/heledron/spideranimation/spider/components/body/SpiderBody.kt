package com.heledron.spideranimation.spider.components.body

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.Posture
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.raycastGround
import com.heledron.spideranimation.utilities.resolveCollision
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.pitch
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Matrix3f
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
    var contact: ContactInfo? = null; private set
    var contactAcceleration = Vector(0.0, 0.0, 0.0); private set

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
        fun create(
        	world: World,
            position: Vector,
            orientation: Quaternionf = Quaternionf(),
            uuid: UUID = UUID.randomUUID(),
        ): SpiderBody {
            return SpiderBody(uuid, world, position.clone(), Quaternionf(orientation))
        }

        fun orientationFrom(up: Vector, forward: Vector, fallback: Vector? = null): Quaternionf {
            val u = up.clone().normalize()
            val f = forward.clone().subtract(u.clone().multiply(forward.dot(u)))
            if (f.lengthSquared() < 1e-8) f.copy(referenceAxis(u, fallback))
            f.normalize()

            val right = u.clone().crossProduct(f) // right = up × forward
            val basis = Matrix3f().set(right.toVector3f(), u.toVector3f(), f.toVector3f())
            return Quaternionf().setFromNormalized(basis)
        }

        private fun referenceAxis(up: Vector, preferred: Vector? = null): Vector {
            if (preferred != null) {
                val projected = preferred.clone().subtract(up.clone().multiply(preferred.dot(up)))
                if (projected.lengthSquared() >= 1e-8) return projected.normalize()
            }
            val basis = if (abs(up.y) < 0.9) UP_VECTOR else FORWARD_VECTOR
            return basis.clone().subtract(up.clone().multiply(basis.dot(up))).normalize()
        }

        fun snapRotationAbout(orientation: Quaternionf, up: Vector, incrementRadians: Float): Quaternionf {
            val axis = up.clone().normalize()
            val reference = referenceAxis(axis)
            val forward = FORWARD_VECTOR.clone().rotate(orientation)
            val planarForward = forward.clone().subtract(axis.clone().multiply(forward.dot(axis)))
            if (planarForward.lengthSquared() < 1e-8) return Quaternionf(orientation)

            planarForward.normalize()
            val angle = atan2(axis.dot(reference.clone().crossProduct(planarForward)), reference.dot(planarForward))
            val snapped = (angle / incrementRadians).roundToInt() * incrementRadians
            return orientationFrom(axis, reference.rotateAroundAxis(axis, snapped.toDouble()))
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
        val diff = newPosition.clone().subtract(position)

        position.copy(newPosition)

        val body = entity.query<SpiderBody>() ?: return
        for (leg in body.legs) leg.endEffector.add(diff)
    }

    // for lerping
    private var preferredPitch = orientation.getEulerAnglesYXZ(Vector3f()).x
    private var preferredRoll = orientation.getEulerAnglesYXZ(Vector3f()).z
    
    private fun updatePreferredAngles(gait: Gait) {
        val up = UP_VECTOR.rotate(gait.scanOrientation.get(this))

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

        val bodyForward = forwardDirection()
        val headingForward = bodyForward.clone().subtract(up.clone().multiply(bodyForward.dot(up)))
        val heading = if (headingForward.lengthSquared() < 1e-8) {
            Quaternionf(orientation)
        } else {
            orientationFrom(up, headingForward)
        }

        val inverseHeading = Quaternionf(heading).invert()
        val forwardLocal = forward.clone().rotate(inverseHeading)
        val sidewaysLocal = sideways.clone().rotate(inverseHeading)

        preferredPitch = forwardLocal.pitch().lerp(preferredPitch, gait.preferredRotationLerpFraction)
        preferredRoll = sidewaysLocal.pitch().lerp(preferredRoll, gait.preferredRotationLerpFraction)

        preferredOrientation = Quaternionf(heading).rotateX(preferredPitch).rotateZ(preferredRoll)
    }

    fun update(ecs: ECS, entity: ECSEntity, options: SpiderOptions) {
        val gait = options.gait

        if (lastAppliedBodyPlan !== options.bodyPlan) {
            legs = options.bodyPlan.legs.map { Leg(ecs, entity, this, it, options) }
            lastAppliedBodyPlan = options.bodyPlan
        }

        updatePreferredAngles(gait)

        val upAxis = UP_VECTOR.rotate(preferredOrientation)

        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / legs.size

        val acceleration = Vector(0.0, -gait.gravityAcceleration, 0.0)

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

        val contact = calcContact(gait)
        this.contact = contact

        // toppling
        val topplingPivot = contact?.centerOfPressure
        val topplingCentre = contact?.centreOfMass
        if (contact != null && !contact.isStable && topplingPivot != null && topplingCentre != null) {
            val lever = topplingCentre.clone().subtract(topplingPivot)
            val gravityDir = Vector(0.0, -1.0, 0.0)
            val horizontalLever = lever.clone().subtract(gravityDir.clone().multiply(lever.dot(gravityDir)))
            val overhang = horizontalLever.length()

            if (overhang > 1e-6) {
                // axis = horizontal lever × down: rotating about it drops the centre of mass
                val axis = horizontalLever.crossProduct(gravityDir).normalize()
                val angularAcceleration = gait.topplingAcceleration * overhang
                rotationalVelocity.add(axis.toVector3f().mul(angularAcceleration.toFloat()))
            }
        }

        contactAcceleration = Vector(0.0, 0.0, 0.0)
        if (contact != null) {
            val preferredPosition = calcPreferredPosition(gait)

            val n = contact.supportAxis.clone().normalize()
            val gripLimit = 
                if (!gait.grip) 0.0 else 
                gait.gripAcceleration * fractionOfLegsGrounded

            // decompose gravity into normal and tangent
            val gravity = Vector(0.0, -gait.gravityAcceleration, 0.0)
            val gravityIntoSurface = gravity.dot(n)
            val gravityTangent = gravity.clone().subtract(n.clone().multiply(gravityIntoSurface))

            // displacement from the preferred height
            val heightError = preferredPosition.clone().subtract(position).dot(n)
            val heightRate = velocity.dot(n)
            val springLimit = gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val heightSpring = (heightError - heightRate).coerceIn(-springLimit, springLimit)


            // push +n
            val support = max(0.0, -gravityIntoSurface * fractionOfLegsGrounded + heightSpring)

            // pull -n
            val gravityPressesIn = gravityIntoSurface < 0.0
            val adhesionForce =
                (if (gravityPressesIn) 0.0 else min(0.0, heightSpring)) - 
                max(0.0, gravityIntoSurface)
            val adhesion = adhesionForce.coerceIn(-gripLimit, 0.0)

            // cancel tangential gravity
            val traction = gravityTangent.multiply(-1.0)
            if (traction.lengthSquared() > gripLimit * gripLimit) {
                traction.normalize().multiply(gripLimit)
            }

            contactAcceleration = n.clone().multiply(support + adhesion).add(traction)
            acceleration.add(contactAcceleration)
        }

        // apply the accumulated forces
        velocity.add(acceleration)

        // drag
        if (!isWalking) {
            val legDrag = 1 - gait.groundDragCoefficient * fractionOfLegsGrounded
            velocity.scaleTangential(upAxis, legDrag)
        }

        if (onGround) {
            velocity.scaleTangential(upAxis, .5)
        }

        velocity.multiply(1 - gait.airDragCoefficient)

        // rotational drag
        rotationalVelocity.mul(1 - gait.rotationalDragCoefficient * fractionOfLegsGrounded.toFloat())
        if (onGround) rotationalVelocity.mul(.5f)

        // apply velocity
        position.add(velocity)

        // resolve collision
        val scanOrientation = gait.scanOrientation.get(this)
        val down = DOWN_VECTOR.rotate(scanOrientation)

        val collision = world.resolveCollision(position, down.clone().multiply(velocity.dot(down)))
        if (collision != null) {
            onGround = true

            val didHit = collision.offset.length() > (gait.gravityAcceleration * 2) * (1 - gait.airDragCoefficient)
            if (didHit) ecs.emit(SpiderBodyHitGroundEvent(spider = this))

            position.copy(collision.position)

            val n = collision.normal
            val intoSurface = velocity.dot(n)
            if (intoSurface < 0) velocity.subtract(n.clone().multiply(intoSurface * (1 + gait.bounceFactor)))
        } else {
            onGround = world.raycastGround(position, down, 0.001) != null
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
        val bodyHeight = posture(gait).bodyHeight

        // feet position
        val feetSupport = if (legs.isEmpty()) null else legs
        	.filter { it.target.isGrounded }
        	.map { it.target.position }.average()

        // ground position
        val lookAhead = position.clone().add(velocity)
        val scanReach = bodyHeight + gait.maxBodyDistanceFromGround
        val scanDirection = up.clone().multiply(-1.0)
        val scanSupport = world.raycastGround(lookAhead, scanDirection, scanReach)?.hitPosition

        // whichever is higher (feet or ground)
        val support = listOfNotNull(feetSupport, scanSupport).maxByOrNull { it.dot(up) }
            ?: return position.clone()

        // keep up component only
        val heightOffset = support.clone().subtract(position).dot(up)
        val target = position.clone().add(up.clone().multiply(heightOffset + bodyHeight))
        val out = position.clone().lerp(target, gait.bodyHeightCorrectionFactor)
        return out
    }

    private fun calcContact(gait: Gait): ContactInfo? {
        val up = UP_VECTOR.rotate(gait.scanOrientation.get(this)).normalize()

        val centreOfMass = legs.map { it.endEffector }.average()
        centreOfMass.lerp(position, 0.5)
        centreOfMass.add(up.clone().multiply(0.01))

        val groundedLegs = legsInPolygonalOrder().map { legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        val legsPolygon = groundedLegs.map { it.endEffector.clone() }

        if (legsPolygon.size == 1) {
            val centerOfPressure = groundedLegs.first().endEffector.clone()
            return ContactInfo(
                supportAxis = centreOfMass.clone().subtract(centerOfPressure).normalize(),
                centerOfPressure = centerOfPressure,
                centreOfMass = centreOfMass,
                contactPolygon = legsPolygon,
                isStable = false,
            )
        }

        val forward = forwardDirection().let { it.subtract(up.clone().multiply(it.dot(up))) }
        if (forward.lengthSquared() < 1e-8) {
            forward.copy(if (abs(up.y) < 0.9) UP_VECTOR else FORWARD_VECTOR)
            forward.subtract(up.clone().multiply(forward.dot(up)))
        }
        forward.normalize()
        val right = up.clone().crossProduct(forward)

        val polygonAlongUp = legsPolygon.map { it.dot(up) }.average()

        fun project(p: Vector) = Vector2d(p.dot(right), p.dot(forward))
        fun unProject(point: Vector2d) = right.clone().multiply(point.x)
            .add(forward.clone().multiply(point.y))
            .add(up.clone().multiply(polygonAlongUp))

        val polygon2D = legsPolygon.map(::project)
        val centre2D = project(centreOfMass)

        if (pointInPolygon(centre2D, polygon2D)) return ContactInfo(
            supportAxis = up.clone(),
            centerOfPressure = unProject(centre2D),
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon,
            isStable = true,
        )

        val nearest = nearestPointInPolygon(centre2D, polygon2D)
        val centerOfPressure = unProject(nearest)
        return ContactInfo(
            supportAxis = centreOfMass.clone().subtract(centerOfPressure).normalize(),
            centerOfPressure = centerOfPressure,
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon,
            isStable = false,
        )
    }
}

class ContactInfo(
    // most of these fields are only used for debug rendering
    val supportAxis: Vector,
    val centerOfPressure: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null,
    val isStable: Boolean = true,
)

fun setupSpiderBody(app: ECS) {
    app.onTick {
        for ((entity, spider, options) in app.query<ECSEntity, SpiderBody, SpiderOptions>()) {
            spider.update(app, entity, options)
        }
    }
}