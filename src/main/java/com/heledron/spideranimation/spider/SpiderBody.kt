package com.heledron.spideranimation.spider

import com.heledron.spideranimation.*
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.entity.Trident
import org.bukkit.util.Vector
import org.joml.Vector2d
import kotlin.math.*


class LegTarget(
    val position: Vector,
    val isGrounded: Boolean,
    val id: Int,
)


class NormalInfo(
    val normal: Vector,
    val origin: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null
)

class Leg(
    val spider: Spider,
    var legPlan: LegPlan
) {
    var triggerZone = triggerZone(); private set
    var comfortZone = comfortZone(); private set
    var restPosition = restPosition(); private set
    var lookAheadPosition = lookAheadPosition(); private set
    var scanStartPosition = scanStartPosition(); private set
    var attachmentPosition = attachmentPosition(); private set
    var scanVector = scanVector(); private set
    var target = locateGround() ?: strandedTarget(); private set
    var isOutsideTriggerZone = false; private set
    var isUncomfortable = false; private set
    var targetOutsideComfortZone = false; private set
    var touchingGround = true; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set
    var chain = KinematicChain(Vector(0, 0, 0), listOf())

    val onStep = EventEmitter()
    var isDisabled = false
    var endEffector = target.position.clone()
    var isPrimary = false
    var canMove = false

    private fun triggerDistance(): Double {
        val maxSpeed = spider.gait.walkSpeed
        val walkFraction = min(spider.velocity.length() / maxSpeed, 1.0)
        val fraction = if (spider.isRotatingYaw || spider.isRotatingPitch) 1.0 else walkFraction

        val diff = spider.gait.legWalkingTriggerDistance - spider.gait.legStationaryTriggerDistance
        return spider.gait.legStationaryTriggerDistance + diff * fraction
    }

    fun isGrounded(): Boolean {
        return touchingGround && !isMoving && !isDisabled
    }

    fun updateMemo() {
        triggerZone = triggerZone()
        comfortZone = comfortZone()
        restPosition = restPosition()
        lookAheadPosition = lookAheadPosition()
        attachmentPosition = attachmentPosition()
        touchingGround = touchingGround()
        scanStartPosition = scanStartPosition()
        scanVector = scanVector()

        isOutsideTriggerZone = !triggerZone.contains(restPosition, endEffector)
        targetOutsideComfortZone = !comfortZone.contains(restPosition, target.position)
        isUncomfortable = !comfortZone.contains(restPosition, endEffector)

        legPlan = spider.bodyPlan.legs.getOrNull(spider.body.legs.indexOf(this)) ?: legPlan
    }

    fun update() {
        updateMovement()
        chain = chain()
    }

    private fun attachmentPosition(): Vector {
        return spider.relativePosition(legPlan.attachmentPosition)
    }

    private fun updateMovement() {
        val gait = spider.gait
        var didStep = false

        timeSinceBeginMove += 1

        // update target
        if (isDisabled) {
            target = disabledTarget()
        } else {
            val ground = locateGround()
            if (ground != null) target = ground

            if (!target.isGrounded || !comfortZone.contains(restPosition, target.position)) {
                target = strandedTarget()
            }
        }

        // inherit parent velocity
        if (!isGrounded()) {
            endEffector.add(spider.velocity)
            rotateAroundY(endEffector, spider.rotateVelocity, spider.location.toVector())
        }

        // resolve ground collision
        if (!touchingGround) {
            val collision = resolveCollision(endEffector.toLocation(spider.location.world!!), DOWN_VECTOR)
            if (collision != null) {
                didStep = true
                touchingGround = true
                endEffector.y = collision.position.y
            }
        }

        if (isMoving) {
            val legMoveSpeed = gait.legMoveSpeed

            endEffector.moveTowards(target.position, legMoveSpeed)

            val targetY = target.position.y + gait.legLiftHeight
            val hDistance = horizontalDistance(endEffector, target.position)
            if (hDistance > gait.legDropDistance) {
                endEffector.y = endEffector.y.moveTowards(targetY, legMoveSpeed)
            }

            if (endEffector.distance(target.position) < 0.0001) {
                isMoving = false

                touchingGround = touchingGround()
                didStep = touchingGround
            }

        } else {
//            canMove = spider.bodyPlan.canMoveLeg(spider, this)
            canMove = if (spider.gait.gallop) GallopGaitType.canMoveLeg(this) else WalkGaitType.canMoveLeg(this)

            if (canMove) {
                isMoving = true
                timeSinceBeginMove = 0
            }
        }

        if (didStep) this.onStep.emit()
    }

    private fun chain(): KinematicChain {
        if (chain.segments.size != legPlan.segments.size) {
            var stride = 0.0
            chain = KinematicChain(attachmentPosition, legPlan.segments.map {
                stride += it.length
                val position = spider.location.toVector().add(legPlan.restPosition.clone().normalize().multiply(stride))
                ChainSegment(position, it.length)
            })
        }

        chain.root.copy(attachmentPosition)

        if (spider.bodyPlan.straightenLegs) {
            val direction = endEffector.clone().subtract(attachmentPosition)
            direction.y = 0.0

            val crossAxis = Vector(0.0, 1.0, 0.0).crossProduct(direction).normalize()

            direction.rotateAroundAxis(crossAxis, Math.toRadians(spider.bodyPlan.legStraightenRotation))

            chain.straightenDirection(direction)
        }

        chain.fabrik(endEffector)

        return chain
    }

    private fun triggerZone(): SplitDistance {
        return SplitDistance(triggerDistance(), spider.gait.legVerticalTriggerDistance)
    }

    private fun comfortZone(): SplitDistance {
        return SplitDistance(spider.gait.legDiscomfortDistance, spider.gait.legVerticalDiscomfortDistance)
    }

    private fun touchingGround(): Boolean {
        return isOnGround(endEffector.toLocation(spider.location.world!!))
    }

    private fun restPosition(): Vector {
        val pos = legPlan.restPosition.clone()
        pos.y -= spider.gait.bodyHeight
        return spider.relativePosition(pos, pitch = 0f)
    }

    private fun lookAheadPosition(): Vector {
        if (!spider.isWalking || spider.velocity.isZero && spider.rotateVelocity == 0.0) return restPosition

        val fraction = min(spider.velocity.length() / spider.gait.walkSpeed, 1.0)
        val mag = fraction * spider.gait.legWalkingTriggerDistance * spider.gait.legLookAheadFraction

        val direction = if (spider.velocity.isZero) spider.location.direction else spider.velocity.clone().normalize()

        val lookAhead = direction.clone().normalize().multiply(mag).add(restPosition)
        rotateAroundY(lookAhead, spider.rotateVelocity, spider.location.toVector())
        return lookAhead
    }

    private fun scanStartPosition(): Vector {
        val vector = spider.relativeVector(Vector(.0, spider.gait.bodyHeight * 1.6, .0), pitch = 0f)
        return lookAheadPosition.clone().add(vector)
    }

    private fun scanVector(): Vector {
        return spider.relativeVector(Vector(.0, -spider.gait.bodyHeight * 3.5, .0), pitch = 0f)
    }

    private fun locateGround(): LegTarget? {
        val lookAhead = lookAheadPosition.toLocation(spider.location.world!!)
        val scanLength = scanVector.length()

        fun candidateAllowed(id: Int): Boolean {
            return true
//            if (!target.isGrounded) return true
//            if (!isMoving) return true
//            return id == target.id
        }

        var id = 0
        fun rayCast(x: Double, z: Double): LegTarget? {
            id += 1

            if (!candidateAllowed(id)) return null

            val start = Location(lookAhead.world, x, scanStartPosition.y, z)
            val hit = raycastGround(start, scanVector, scanLength) ?: return null

            return LegTarget(position = hit.hitPosition, isGrounded = true, id = id)
        }

        val x = lookAhead.x
        val z = lookAhead.z

        val mainCandidate = rayCast(x, z)

        if (!spider.gait.legScanAlternativeGround) return mainCandidate

        if (mainCandidate != null) {
            if (mainCandidate.position.y in lookAhead.y - .24 .. lookAhead.y + 1.5) {
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

        val preferredPosition = lookAhead.toVector()

        val frontBlock = lookAhead.clone().add(spider.location.direction.clone().multiply(1)).block
        if (!frontBlock.isPassable) preferredPosition.y += spider.gait.legScanHeightBias

        val best = candidates
            .filterNotNull()
            .minByOrNull { it.position.distanceSquared(preferredPosition) }

        if (best != null && !comfortZone.contains(restPosition, best.position)) {
            return null
        }

        return best
    }

    private fun strandedTarget(): LegTarget {
        return LegTarget(position = lookAheadPosition.clone(), isGrounded = false, id = -1)
    }

    private fun disabledTarget(): LegTarget {
        val target = strandedTarget()
        target.position.y += spider.gait.bodyHeight / 2

        val groundPosition = raycastGround(endEffector.toLocation(spider.location.world!!).add(0.0, .5, 0.0), DOWN_VECTOR, 2.0)?.hitPosition
        if (groundPosition != null && groundPosition.y > target.position.y) target.position.y = groundPosition.y + spider.gait.bodyHeight * .3

        return target
    }
}


class SpiderBody(val spider: Spider): SpiderComponent {
    val onHitGround = EventEmitter()
    var onGround = false; private set
    var legs = spider.bodyPlan.legs.map { Leg(spider, it) }
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector(0.0, 0.0, 0.0); private set
    val onGetHitByTrident = EventEmitter()

    override fun update() {
        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / spider.body.legs.size

        // apply gravity and air resistance
        spider.velocity.y -= spider.gait.gravityAcceleration
        spider.velocity.y *= (1 - spider.gait.airDragCoefficient)

        // apply ground drag
        if (!spider.isWalking) {
            val drag = spider.gait.groundDragCoefficient * fractionOfLegsGrounded
            spider.velocity.x *= drag
            spider.velocity.z *= drag
        }

        if (onGround) {
            spider.velocity.x *= .5
            spider.velocity.z *= .5
        }


        normal = getNormal(spider)

        normalAcceleration = Vector(0.0, 0.0, 0.0)
        normal?.let {
            val preferredY = getPreferredY()
            val preferredYAcceleration = (preferredY - spider.location.y - spider.velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = spider.gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = it.normal.clone().multiply(accelerationMagnitude)

            // if the horizontal acceleration is too high,
            // there's no point accelerating as the spider will fall over anyway
            if (horizontalLength(normalAcceleration) > normalAcceleration.y) normalAcceleration.multiply(0.0)

            spider.velocity.add(normalAcceleration)
        }

        // apply velocity
        spider.location.add(spider.velocity)

        // resolve collision
        val collision = resolveCollision(spider.location, Vector(0.0, min(-1.0, -abs(spider.velocity.y)), 0.0))
        if (collision != null) {
            onGround = true

            val didHit = collision.offset.length() > (spider.gait.gravityAcceleration * 2) * (1 - spider.gait.airDragCoefficient)
            if (didHit) onHitGround.emit()

            spider.location.y = collision.position.y
            if (spider.velocity.y < 0) spider.velocity.y *= -spider.gait.bounceFactor
            if (spider.velocity.y < spider.gait.gravityAcceleration) spider.velocity.y = .0
        } else {
            onGround = isOnGround(spider.location)
        }

        val updateOrder = getLegsInUpdateOrder(spider)
        for (leg in updateOrder) leg.updateMemo()
        for (leg in updateOrder) leg.update()


        val tridents = spider.location.world!!.getNearbyEntities(spider.location, 1.5, 1.5, 1.5) {
            it is Trident && it.shooter != spider.mount.getRider()
        }
        for (trident in tridents) {
            if (trident != null && trident.velocity.length() > 2.0) {
                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                onGetHitByTrident.emit()

                spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))
            }
        }
    }

    private fun getPreferredY(): Double {
    //        val groundY = getGround(spider.location) + .3
        val averageY = spider.body.legs.map { it.target.position.y }.average() + spider.gait.bodyHeight
        val targetY = averageY //max(averageY, groundY)
        val stabilizedY = spider.location.y.lerp(targetY, spider.gait.bodyHeightCorrectionFactor)
        return stabilizedY
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
    }

    private fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        val diagonal1 = legs.indices.filter { LegLookUp.isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { LegLookUp.isDiagonal2(it) }
        val indices = diagonal1 + diagonal2
        return indices.map { spider.body.legs[it] }
    }

    private fun getNormal(spider: Spider): NormalInfo? {
        if (spider.gait.useLegacyNormalForce) {
            val pairs = LegLookUp.diagonalPairs(legs.indices.toList())
            if (pairs.any { pair -> pair.mapNotNull { spider.body.legs.getOrNull(it) }.all { it.isGrounded() } }) {
                return NormalInfo(normal = Vector(0, 1, 0))
            }

            return null
        }

        val centreOfMass = averageVector(spider.body.legs.map { it.endEffector })
        centreOfMass.lerp(spider.location.toVector(), 0.5)
        centreOfMass.y += 0.01

        val groundedLegs = legsInPolygonalOrder().map { spider.body.legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        fun applyStabilization(normal: NormalInfo) {
            if (normal.origin == null) return
            if (normal.centreOfMass == null) return

            if (horizontalDistance(normal.origin, normal.centreOfMass) < spider.gait.polygonLeeway) {
                normal.origin.x = normal.centreOfMass.x
                normal.origin.z = normal.centreOfMass.z
            }

            val stabilizationTarget = normal.origin.clone().setY(normal.centreOfMass.y)
            normal.centreOfMass.lerp(stabilizationTarget, spider.gait.stabilizationFactor)

            normal.normal.copy(normal.centreOfMass).subtract(normal.origin).normalize()
        }

        val legsPolygon = groundedLegs.map { it.endEffector.clone() }
        val polygonCenterY = legsPolygon.map { it.y }.average()

        if (legsPolygon.size > 1) {
            val polygon2D = legsPolygon.map { Vector2d(it.x, it.z) }

            if (pointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)) {
                // inside polygon. accelerate upwards towards centre of mass
                return NormalInfo(
                    normal = Vector(0, 1, 0),
                    origin = Vector(centreOfMass.x, polygonCenterY, centreOfMass.z),
                    centreOfMass = centreOfMass,
                    contactPolygon = legsPolygon
                )
            } else {
                // outside polygon, accelerate at an angle from within the polygon
                val point = nearestPointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)
                val origin = Vector(point.x, polygonCenterY, point.y)
                return NormalInfo(
                    normal = centreOfMass.clone().subtract(origin).normalize(),
                    origin = origin,
                    centreOfMass = centreOfMass,
                    contactPolygon = legsPolygon
                ).apply { applyStabilization(this )}
            }
        } else {
            // only 1 leg on ground
            val origin = groundedLegs.first().endEffector.clone()
            return NormalInfo(
                normal = centreOfMass.clone().subtract(origin).normalize(),
                origin = origin,
                centreOfMass = centreOfMass,
                contactPolygon = legsPolygon
            ).apply { applyStabilization(this )}
        }
    }
}