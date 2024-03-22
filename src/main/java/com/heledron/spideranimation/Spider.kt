package com.heledron.spideranimation

import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Vector
import java.util.ArrayList
import kotlin.math.*

data class Gait(
    var walkSpeed: Double = .15,
    var legSpeed: Double = walkSpeed * 3
) {
    var walkAcceleration = walkSpeed / 7
    var rotateSpeed = walkSpeed

    var legLiftHeight = .35
    var legDropDistance = legLiftHeight

    var legStationaryTriggerDistance = .25
    var legMovingTriggerDistance = .9
    var legDiscomfortDistance = 1.2

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

    var bodyHeight = 1.1

    var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    var bodyHeightCorrectionFactor = .25

    var legStraightenHeight = 1.25
    var legNoStraighten = false

    var legSegmentLength = 1.0
    var legSegmentCount = 3

    var legScanGround = true
    var applyGravity = true
    var legAlwaysCanMove = false
    var legScanAlternativeGround = true
    var legApplyScanHeightBias = true
}


class Spider(val location: Location, val gait: Gait) {
    var behaviour: SpiderBehaviour = StayStillBehaviour

    val velocity = Vector(0.0, 0.0, 0.0)

    val leftFrontLeg = createLeg(Vector(0.9, -gait.bodyHeight, 0.9), 0.9 * gait.legSegmentLength, gait.legSegmentCount)
    val rightFrontLeg = createLeg(Vector(-0.9, -gait.bodyHeight, 0.9), 0.9 * gait.legSegmentLength, gait.legSegmentCount)
    val leftBackLeg = createLeg(Vector(1.0, -gait.bodyHeight, -1.1), 1.2 * gait.legSegmentLength, gait.legSegmentCount)
    val rightBackLeg = createLeg(Vector(-1.0, -gait.bodyHeight, -1.1), 1.2 * gait.legSegmentLength, gait.legSegmentCount)
    val legs = ArrayList<Leg>()

    val blockBones = ArrayList<BlockDisplay>()
    val itemBones = ArrayList<ItemDisplay>()

    var isRotating = false
    var didHitGround = false

    init {
        location.y += gait.bodyHeight
        legs.add(leftFrontLeg)
        legs.add(rightFrontLeg)
        legs.add(leftBackLeg)
        legs.add(rightBackLeg)
    }

    fun accelerateToVelocity(targetVelocity: Vector) {
        val target = targetVelocity.clone()
        if (legs.any { it.uncomfortable }) {
            target.multiply(0)
        }
        lerpVectorByConstant(velocity, target.setY(velocity.y), gait.walkAcceleration)
    }

    fun rotateTowards(targetDirection: Vector) {
        location.yaw %= 360
        val oldYaw = Math.toRadians(location.yaw.toDouble())

        val targetYaw = atan2(-targetDirection.x, targetDirection.z)

        val optimizedTargetYaw = if (abs(targetYaw - oldYaw) > PI) {
            if (targetYaw > oldYaw) targetYaw - PI * 2 else targetYaw + PI * 2
        } else {
            targetYaw
        }

        isRotating = abs(optimizedTargetYaw - oldYaw) > 0.0001

        if (!isRotating || legs.any { it.uncomfortable }) return

        val newYaw = lerpNumberByConstant(oldYaw, optimizedTargetYaw, gait.rotateSpeed)
        location.yaw = Math.toDegrees(newYaw).toFloat()

        // rotate legs end effector
        for (leg in legs) {
            if (leg.isGrounded()) continue
            val vector = leg.endEffector.clone().subtract(location.toVector())
            vector.rotateAroundY(newYaw - oldYaw)
            leg.endEffector.copy(location.toVector()).add(vector)
        }
    }

    fun teleport(newLocation: Location) {
        val diff = newLocation.toVector().subtract(location.toVector())

        location.world = newLocation.world
        location.x = newLocation.x
        location.y = newLocation.y
        location.z = newLocation.z

        for (leg in legs) {
            leg.endEffector.add(diff)
            for (segment in leg.chain.segments) {
                segment.position.add(diff)
            }
        }
    }

    fun update() {
        // update behaviour
        didHitGround = false
        isRotating = false
        behaviour.update(this)

        // apply gravity and air resistance
        val bounce = Vector(0.0, 0.0, 0.0)
        if (gait.legScanGround) {
            if (gait.applyGravity) {
                velocity.y -= gait.gravityAcceleration
                velocity.y *= (1 - gait.airDragCoefficient)
            }

            if (isGrounded()) {
                // adjust body height
                val legsAverageY = legs.map { it.targetPosition.y }.average()
                val targetY = legsAverageY + gait.bodyHeight
                val stabilizedY = lerpNumberByFactor(location.y, targetY, gait.bodyHeightCorrectionFactor)
                val maxThrust = gait.bodyHeightCorrectionAcceleration

                // normally, we allow gravity to apply the downwards force,
                // but if there is none, we need to do it ourselves so the spider doesn't float
                val minThrust = if (!gait.applyGravity) -maxThrust else 0.0

                val thrust = (stabilizedY - location.y - velocity.y).coerceIn(minThrust, maxThrust)
                velocity.y += thrust
            }

            // resolve ground collision
            val resolveY = resolveGroundCollision(location.clone().add(velocity))
            if (resolveY > 0.0) {
                location.y += resolveY
                if (velocity.y < 0)bounce.y = -velocity.y * gait.bounceFactor

                didHitGround = resolveY > (gait.gravityAcceleration * 2) * (1 - gait.airDragCoefficient)
            }
        }

        // apply velocity
        location.add(velocity)
        velocity.add(bounce)

        // move legs
        for (leg in legs) {
            leg.update()
        }
    }

    fun adjacentLegs(leg: Leg): List<Leg> {
        return when (leg) {
            leftFrontLeg -> listOf(rightFrontLeg, leftBackLeg)
            rightFrontLeg -> listOf(rightBackLeg, leftFrontLeg)
            leftBackLeg -> listOf(rightBackLeg, leftFrontLeg)
            rightBackLeg -> listOf(rightFrontLeg, leftBackLeg)
            else -> listOf()
        }
    }

    fun isGrounded(): Boolean {
        if (leftFrontLeg.isGrounded() && rightBackLeg.isGrounded()) return true
        if (rightFrontLeg.isGrounded() && leftBackLeg.isGrounded()) return true
        return false
    }

    private fun createLeg(restPosition: Vector, segmentLength: Double, segmentsCount: Int): Leg {
        val segments = arrayListOf<ChainSegment>()
        for (i in 1 .. segmentsCount) {
            val position = location.toVector().add(restPosition.clone().normalize().multiply(segmentLength * i))
            segments.add(ChainSegment(position, segmentLength))
        }
        val chain = KinematicChain(location.toVector(), segments)
        return Leg(this, restPosition, chain)
    }
}

class Leg(
    val parent: Spider,
    val relativeRestPosition: Vector,
    val chain: KinematicChain,
) {
    val scanGroundAbove = 2.0
    val scanGroundBelow = 2.0

    var restPosition = restPosition()
    var targetPosition = locateGround()?.toVector() ?: restPosition.clone()
    var endEffector = targetPosition.clone()

    var uncomfortable = false
    var onGround = true
    var isMoving = false
    var isStranded = false

    var didStep = false

    fun triggerDistance(): Double {
        val isMoving = parent.velocity.x != 0.0 || parent.velocity.z != 0.0
        return if (!isMoving && !parent.isRotating) parent.gait.legStationaryTriggerDistance else parent.gait.legMovingTriggerDistance
    }

    fun isGrounded(): Boolean {
        return onGround && !isMoving
    }

    fun update() {
        updateMovement()

        chain.root.copy(parent.location.toVector())


        if (!parent.gait.legNoStraighten) chain.straighten(endEffector, parent.gait.legStraightenHeight)
        chain.fabrik(endEffector)
    }

    private fun updateMovement() {
        val gait = parent.gait

        didStep = false

        restPosition = restPosition()

        val ground = locateGround()
        targetPosition = ground?.toVector() ?: restPosition.clone()
        isStranded = ground == null

        val distanceToTarget =  horizontalDistance(endEffector, targetPosition)
        uncomfortable = !isMoving && distanceToTarget > triggerDistance() && horizontalDistance(restPosition, endEffector) > gait.legDiscomfortDistance
        onGround = onGround()

        // inherit parent velocity
        if (!isGrounded()) {
            endEffector.add(parent.velocity)
        }

        // resolve ground collision
        if (!onGround) {
            onGround = onGround()
            didStep = onGround

            if (gait.legScanGround) {
                val yCollision = resolveGroundCollision(endEffector.toLocation(parent.location.world))
                endEffector.y += yCollision
            }
        }

        if (isMoving) {
            // move leg
            val moveSpeed = gait.legSpeed

            lerpVectorByConstant(endEffector, targetPosition, moveSpeed)

            val targetY = targetPosition.y + gait.legLiftHeight
            val hDistance = horizontalDistance(endEffector, targetPosition)
            if (hDistance > gait.legDropDistance) {
                endEffector.y = lerpNumberByConstant(endEffector.y, targetY, moveSpeed)
            }

            if (endEffector.distance(targetPosition) < 0.0001) {
                isMoving = false

                onGround = onGround()
                didStep = onGround
            }

        } else {
            // begin moving leg
            val verticalDistance = verticalDistance(endEffector, targetPosition)
            val canMove = gait.legAlwaysCanMove || isStranded || parent.adjacentLegs(this).all { !it.isMoving }
            if (canMove && (distanceToTarget > triggerDistance() || verticalDistance >= 0.0001)) {
                isMoving = true
            }
        }
    }

    private fun onGround(): Boolean {
        if (!parent.gait.legScanGround) return !isMoving
        return isOnGround(endEffector.toLocation(parent.location.world))
    }

    private fun restPosition(): Vector {
        return parent.location.toVector().add(relativeRestPosition.clone().rotateAroundY(-Math.toRadians(parent.location.yaw.toDouble())))
    }

    private fun locateGround(): Location? {
        val location = restPosition.toLocation(parent.location.world)

        if (!parent.gait.legScanGround) return location

        fun rayCast(x: Double, z: Double): Location? {
            val y = location.y + scanGroundAbove
            val startScan = Location(location.world, x, y, z)
            val hit = startScan.world.rayTraceBlocks(startScan, Vector(0.0, -1.0, 0.0), scanGroundAbove + scanGroundBelow, FluidCollisionMode.NEVER, true)
            return hit?.hitPosition?.toLocation(startScan.world)
        }

        val x = restPosition.x
        val z = restPosition.z

        val mainCandidate = rayCast(x, z)

        if (!parent.gait.legScanAlternativeGround) return mainCandidate

        if (mainCandidate != null && parent.gait.legApplyScanHeightBias) {
            if (mainCandidate.y in location.y - .24 .. location.y + 1.5) {
                return mainCandidate
            }
        }

        val margin = 2 / 16.0
        val nx = floor(x) - margin
        val nz = floor(z) - margin
        val pz = ceil(z) + margin
        val px = ceil(x) + margin

        val candidates = listOf(
            rayCast(nx, nz), rayCast(nx, z), rayCast(nx, pz),
            rayCast(x, nz),  mainCandidate,  rayCast(x, pz),
            rayCast(px, nz), rayCast(px, z), rayCast(px, pz),
        )

         val preferredLocation = location.clone()
         if (parent.gait.legApplyScanHeightBias) {
             val frontBlock = location.clone().add(parent.location.direction.clone().multiply(1)).block
             if (!frontBlock.isPassable) preferredLocation.y += .5
         }

        // return closest to preferred location
         return candidates.minByOrNull { it?.distanceSquared(preferredLocation) ?: Double.MAX_VALUE }
    }
}

fun isOnGround(location: Location): Boolean {
    val adjustedLocation = location.add(0.0, -0.0001, 0.0)
    val block = adjustedLocation.block

    if (block.isPassable) return false

    val boundingBox = block.boundingBox
    return boundingBox.contains(location.toVector())
}

fun resolveGroundCollision(location: Location): Double {
    val block = location.block
    if (block.isPassable) return 0.0
    val boundingBox = block.boundingBox
    return if (boundingBox.contains(location.toVector())) boundingBox.maxY - location.y else 0.0
}

fun lerpNumberByFactor(current: Double, target: Double, factor: Double): Double {
    return current + (target - current) * factor
}

fun lerpNumberByConstant(current: Double, target: Double, constant: Double): Double {
    val distance = target - current
    return if (abs(distance) < constant) target else current + constant * distance.sign
}

fun lerpVectorByConstant(current: Vector, target: Vector, constant: Double) {
    val diff = target.clone().subtract(current)
    val distance = diff.length()
    if (distance <= constant) {
        current.copy(target)
    } else {
        current.add(diff.multiply(constant / distance))
    }
}

fun verticalDistance(a: Vector, b: Vector): Double {
    return abs(a.y - b.y)
}

fun horizontalDistance(a: Vector, b: Vector): Double {
    val x = a.x - b.x
    val z = a.z - b.z
    return sqrt(x * x + z * z)
}

fun horizontalDistance(a: Location, b: Location): Double {
    val x = a.x - b.x
    val z = a.z - b.z
    return sqrt(x * x + z * z)
}