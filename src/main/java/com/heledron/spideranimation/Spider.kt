package com.heledron.spideranimation

import com.heledron.spideranimation.components.*
import org.bukkit.Location
import org.bukkit.util.Vector
import java.io.Closeable
import kotlin.math.*


interface SpiderComponent : Closeable {
    fun update() {}
    fun render() {}
    override fun close() {}
}

object EmptyComponent : SpiderComponent

class Gait(
    walkSpeed: Double,
    gallopBreakpoint: Double,
) {
    companion object {
        fun defaultWalk(): Gait {
            return Gait(.15, 10000.0)
        }

        fun defaultGallop(): Gait {
            return Gait(.4, .3).apply {
                legMoveCooldown = 1
                legMoveSpeed = .6
            }
        }
    }

    fun getScale() = zzzStoredScale

    fun scale(scale: Double) {
        this.zzzStoredScale *= scale

        walkSpeed *= scale
        walkAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        legDropDistance *= scale
        legStationaryTriggerDistance *= scale
        legWalkingTriggerDistance *= scale
        legDiscomfortDistance *= scale
        legVerticalTriggerDistance *= scale
        legVerticalDiscomfortDistance *= scale
        bodyHeight *= scale
        legScanHeightBias *= scale
    }


    /** Used for tracking the scale. It's prefixed with zzz so that it appears last in auto-completion */
    @KVElement var zzzStoredScale = 1.0

    @KVElement var gallopBreakpoint = gallopBreakpoint

    @KVElement var walkSpeed = walkSpeed
    @KVElement var walkAcceleration = .15 / 4

    @KVElement var rotateSpeed = .15

    @KVElement var legMoveSpeed = walkSpeed * 3

    @KVElement var legLiftHeight = .35
    @KVElement var legDropDistance = legLiftHeight

    @KVElement var legStationaryTriggerDistance = .25
    @KVElement var legWalkingTriggerDistance = .8
    @KVElement var legDiscomfortDistance = 1.2

    @KVElement var legVerticalTriggerDistance = 1.5
    @KVElement var legVerticalDiscomfortDistance = 1.6

    @KVElement var gravityAcceleration = .08
    @KVElement var airDragCoefficient = .02
    @KVElement var bounceFactor = .5

    @KVElement var bodyHeight = 1.1

    @KVElement var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    @KVElement var bodyHeightCorrectionFactor = .25

    @KVElement var legStraightenRotation = -60.0
    @KVElement var legStraightenMinRotation = -90.0
    @KVElement var legStraightenMaxRotation = -20.0
    @KVElement var legNoStraighten = false

    @KVElement var legScanAlternativeGround = true
    @KVElement var legScanHeightBias = .5

    @KVElement var tridentKnockBack = .3
    @KVElement var legLookAheadFraction = .6
    @KVElement var groundDragCoefficient = .2

    @KVElement var legMoveCooldown = 2

    @KVElement var adjustLookAheadDistance = true

    @KVElement var useLegacyNormalForce = false
    @KVElement var polygonLeeway = .0
    @KVElement var stabilizationFactor = 0.7
}


class Spider(val location: Location, var gait: Gait, val bodyPlan: SpiderBodyPlan): Closeable {
    var isWalking = false; private set
    var isRotatingYaw = false; private set
    var isRotatingPitch = false; private set
    var rotateVelocity = 0.0; private set

    val velocity = Vector(0.0, 0.0, 0.0)
    init { location.y += gait.bodyHeight }

    val body = SpiderBody(this)

    val cloak = Cloak(this)
    val sound = SoundEffects(this)
    var mount = Mountable(this)

    var behaviour: SpiderComponent = StayStillBehaviour(this)
    set (value) {
        field.close()
        field = value
    }
    var renderer: SpiderComponent = SpiderEntityRenderer(this)
    set (value) {
        field.close()
        field = value
    }

    var debugRenderer: SpiderComponent? = null
    set (value) {
        field?.close()
        field = value
    }

    val isGalloping get() = velocity.length() > gait.gallopBreakpoint * gait.walkSpeed && isWalking

    val pointerDetector = PointDetector(this)

    override fun close() {
        getComponents().forEach { it.close() }
    }

    fun teleport(newLocation: Location) {
        val diff = newLocation.toVector().subtract(location.toVector())

        copyLocation(location, newLocation)

        for (leg in body.legs) {
            leg.endEffector.add(diff)
            for (segment in leg.chain.segments) segment.position.add(diff)
        }
    }

    fun rotateTowards(targetDirection: Vector) {
        // pitch
        val targetPitch = -Math.toDegrees(atan2(targetDirection.y, horizontalLength(targetDirection))).coerceIn(-30.0, 30.0)
        val oldPitch = location.pitch
        location.pitch = lerpNumberByConstant(oldPitch.toDouble(), targetPitch, Math.toDegrees(gait.rotateSpeed)).toFloat()//.coerceIn(minPitch, maxPitch)
        isRotatingPitch = abs(targetPitch - oldPitch) > 0.0001

        // yaw
        val maxSpeed = gait.rotateSpeed * body.legs.filter { it.isGrounded() }.size / body.legs.size
        location.yaw %= 360
        val oldYaw = Math.toRadians(location.yaw.toDouble())
        val targetYaw = atan2(-targetDirection.x, targetDirection.z)

        val optimizedTargetYaw = if (abs(targetYaw - oldYaw) > PI) {
            if (targetYaw > oldYaw) targetYaw - PI * 2 else targetYaw + PI * 2
        } else {
            targetYaw
        }

        isRotatingYaw = abs(optimizedTargetYaw - oldYaw) > 0.0001

        rotateVelocity = 0.0
        if (!isRotatingYaw || body.legs.any { it.isUncomfortable && !it.isMoving }) return

        val newYaw = lerpNumberByConstant(oldYaw, optimizedTargetYaw, maxSpeed)
        location.yaw = Math.toDegrees(newYaw).toFloat()

        rotateVelocity = -(newYaw - oldYaw)
    }

    fun walkAt(targetVelocity: Vector) {
        val acceleration = gait.walkAcceleration// * body.legs.filter { it.isGrounded() }.size / body.legs.size
        val target = targetVelocity.clone()
        if (body.legs.any { it.isUncomfortable && !it.isMoving } && !isGalloping) {
            lerpVectorByConstant(velocity, Vector(0, 0, 0), acceleration)
            isWalking = true
        } else {
            lerpVectorByConstant(velocity, target.setY(velocity.y), acceleration)
            isWalking = velocity.x != 0.0 && velocity.z != 0.0
        }
    }

    fun getComponents() = iterator<SpiderComponent> {
        yield(cloak)
        yield(behaviour)
        yield(body)
        yield(renderer)
        yield(debugRenderer ?: EmptyComponent)
        yield(sound)
        yield(mount)
        yield(pointerDetector)
    }

    fun update() {
        // update behaviour
        isRotatingYaw = false
        rotateVelocity = 0.0
        isWalking = false

        for (component in getComponents()) component.update()

        for (component in getComponents()) component.render()
    }

    fun relativePosition(point: Vector, pitch: Float = location.pitch): Vector {
        return location.toVector().add(relativeVector(point, pitch))
    }

    fun relativeVector(point: Vector, pitch: Float = location.pitch): Vector {
        val out = point.clone()
        out.rotateAroundX(Math.toRadians(pitch.toDouble()))
        out.rotateAroundY(-Math.toRadians(location.yaw.toDouble()))
        return out
    }
}

