package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.ChainSegment
import com.heledron.spideranimation.KinematicChain
import com.heledron.spideranimation.spider.GallopGaitType
import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.WalkGaitType
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaterniond
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class Leg(
    val spider: Spider,
    var legPlan: LegPlan
) {
    // memo
    lateinit var triggerZone: SplitDistanceZone; private set
//    val triggerZoneCenter: Vector; get () = restPosition.clone()
    lateinit var comfortZone: SplitDistanceZone; private set
//    lateinit var comfortZoneCenter: Vector; private set

    lateinit var restPosition: Vector; private set
    lateinit var lookAheadPosition: Vector; private set
    lateinit var scanStartPosition: Vector; private set
    lateinit var scanVector: Vector; private set

    lateinit var attachmentPosition: Vector; private set

    init {
        updateMemo()
    }

    // state
    var target = locateGround() ?: strandedTarget()
    var endEffector = target.position.clone()
    var chain = KinematicChain(Vector(0, 0, 0), listOf())

    var touchingGround = true; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set

    var isDisabled = false
    var isPrimary = false
    var canMove = false

    // utils
    val isOutsideTriggerZone: Boolean; get () { return !triggerZone.contains(endEffector) }
    val isUncomfortable: Boolean; get () { return !comfortZone.contains(endEffector) }

    // events
    val onStep = EventEmitter()

    fun isGrounded(): Boolean {
        return touchingGround && !isMoving && !isDisabled
    }

    fun updateMemo() {
        val orientation = spider.preferredOrientation

        val scanStartAxis = Vector(0.0, spider.gait.bodyHeight * 1.6, .0).rotate(orientation)
        val scanAxis = Vector(0.0, -spider.gait.bodyHeight * 3.5, .0).rotate(orientation)

        // rest position
        restPosition = legPlan.restPosition.clone()
        restPosition.y -= spider.gait.bodyHeight
        restPosition.rotate(orientation).add(spider.position)

        // trigger zone
        triggerZone = SplitDistanceZone(restPosition, triggerZoneSize())

        // comfort zone
        // we want the comfort zone to extend above the spider's body
        // and below the rest position
        val comfortZoneCenter = restPosition.clone()
        comfortZoneCenter.y = restPosition.y.lerp(spider.position.y, .5)
        val comfortZoneSize = SplitDistance(
            horizontal = spider.gait.comfortZone.horizontal,
            vertical = spider.gait.comfortZone.vertical + (spider.position.y - restPosition.y)
        )
        comfortZone = SplitDistanceZone(comfortZoneCenter, comfortZoneSize)

        // lookahead
        lookAheadPosition = lookAheadPosition(restPosition, triggerZone.size.horizontal)

        // scan
        scanStartPosition = lookAheadPosition.clone().add(scanStartAxis)
        scanVector = scanAxis

        // attachment position
        attachmentPosition = legPlan.attachmentPosition.clone().rotate(spider.orientation).add(spider.position)
    }

    fun update() {
        legPlan = spider.options.bodyPlan.legs.getOrNull(spider.body.legs.indexOf(this)) ?: legPlan
        updateMovement()
        chain = chain()
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

            if (!target.isGrounded || !comfortZone.contains(target.position)) {
                target = strandedTarget()
            }
        }

        // inherit parent velocity
        if (!isGrounded()) {
            endEffector.add(spider.velocity)
            endEffector.rotateAroundY(spider.yawVelocity, spider.position)
        }

        // resolve ground collision
        if (!touchingGround) {
            val collision = resolveCollision(endEffector.toLocation(spider.world), DOWN_VECTOR)
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
                val position = spider.position.clone().add(legPlan.restPosition.clone().normalize().multiply(stride))
                ChainSegment(position, it.length, it.initDirection)
            })
        }

        chain.root.copy(attachmentPosition)

        if (spider.options.bodyPlan.straightenLegs) {
            val direction = endEffector.clone().subtract(attachmentPosition)

            val rotation = Quaterniond().rotationToYX(FORWARD_VECTOR.toVector3d(), direction.toVector3d())
            rotation.rotateX(Math.toRadians(spider.options.bodyPlan.legStraightenRotation))

//            val crossAxis = Vector(0.0, 1.0, 0.0).crossProduct(direction).normalize()
//            direction.rotateAroundAxis(crossAxis, Math.toRadians(spider.options.bodyPlan.legStraightenRotation))
//            direction.rotate(Quaterniond().rotateLocalX(spider.options.bodyPlan.legStraightenRotation))

            chain.straightenDirection(rotation)
        }

        if (!spider.options.debug.disableFabrik) chain.fabrik(endEffector)

        return chain
    }

    private fun touchingGround(): Boolean {
        return spider.world.isOnGround(endEffector, DOWN_VECTOR.rotate(spider.orientation))
    }

    private fun triggerZoneSize(): SplitDistance {
        if (spider.isRotatingYaw) return spider.gait.walkingTriggerZone

        val fraction = min(spider.velocity.length() / spider.gait.walkSpeed, 1.0)
        return spider.gait.stationaryTriggerZone.lerp(spider.gait.walkingTriggerZone, fraction)
    }

    private fun lookAheadPosition(restPosition: Vector, triggerZoneRadius: Double): Vector {
        if (!spider.isWalking) return restPosition

        val direction = if (spider.velocity.isZero) spider.forwardDirection() else spider.velocity.clone().normalize()

        val lookAhead = direction.multiply(triggerZoneRadius * spider.gait.legLookAheadFraction).add(restPosition)
        lookAhead.rotateAroundY(spider.yawVelocity, spider.position)
        return lookAhead
    }

    private fun locateGround(): LegTarget? {
        val lookAhead = lookAheadPosition.toLocation(spider.world)
        val scanLength = scanVector.length()

        fun candidateAllowed(id: Int): Boolean {
            return true
        }

        var id = 0
        val world = spider.world
        fun rayCast(x: Double, z: Double): LegTarget? {
            id += 1

            if (!candidateAllowed(id)) return null

            val start = Location(world, x, scanStartPosition.y, z)
            val hit = raycastGround(start, scanVector, scanLength) ?: return null

            return LegTarget(position = hit.hitPosition, isGrounded = true, id = id)
        }

        val x = scanStartPosition.x
        val z = scanStartPosition.z

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

        val frontBlock = lookAhead.clone().add(spider.forwardDirection().clone().multiply(1)).block
        if (!frontBlock.isPassable) preferredPosition.y += spider.gait.legScanHeightBias

        val best = candidates
            .filterNotNull()
            .minByOrNull { it.position.distanceSquared(preferredPosition) }

        if (best != null && !comfortZone.contains(best.position)) {
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

        val groundPosition = raycastGround(endEffector.toLocation(spider.world).add(0.0, .5, 0.0), DOWN_VECTOR, 2.0)?.hitPosition
        if (groundPosition != null && groundPosition.y > target.position.y) target.position.y = groundPosition.y + spider.gait.bodyHeight * .3

        return target
    }
}

class LegTarget(
    val position: Vector,
    val isGrounded: Boolean,
    val id: Int,
)