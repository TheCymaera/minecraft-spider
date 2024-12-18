package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.utilities.ChainSegment
import com.heledron.spideranimation.utilities.KinematicChain
import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.ceil
import kotlin.math.floor

class Leg(
    val spider: Spider,
    var legPlan: LegPlan
) {
    // memo
    lateinit var triggerZone: SplitDistanceZone; private set
    lateinit var comfortZone: SplitDistanceZone; private set

    var groundPosition: Vector? = null; private set
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
    var previousEndEffector = endEffector.clone()
    var chain = KinematicChain(Vector(0, 0, 0), listOf())

    var touchingGround = true; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set
    var timeSinceStopMove = 0; private set

    var isDisabled = false
    var isPrimary = false
    var canMove = false

    // utils
    val isOutsideTriggerZone: Boolean; get () { return !triggerZone.contains(endEffector) }
    val isUncomfortable: Boolean; get () { return !comfortZone.contains(endEffector) }

    // events
    val onStep = EventEmitter()

    init {
        onStep.listen { timeSinceStopMove = 0 }
    }

    fun isGrounded(): Boolean {
        return touchingGround && !isMoving && !isDisabled
    }

    fun updateMemo() {
        val orientation = spider.gait.scanPivotMode.get(spider)

        val upVector = UP_VECTOR.rotate(orientation)
        val scanStartAxis = upVector.clone().multiply(spider.lerpedGait.bodyHeight * 1.6)
        val scanAxis = upVector.clone().multiply(-spider.lerpedGait.bodyHeight * 3.5)

        // rest position
        restPosition = legPlan.restPosition.clone()
        restPosition.add(upVector.clone().multiply(-spider.lerpedGait.bodyHeight))
        restPosition.rotate(orientation).add(spider.position)

        // trigger zone
        triggerZone = SplitDistanceZone(restPosition, spider.lerpedGait.triggerZone)

        // comfort zone
        // we want the comfort zone to extend above the spider's body
        // and below the rest position
        val comfortZoneCenter = restPosition.clone()
        comfortZoneCenter.y = restPosition.y.lerp(spider.position.y, .5)
        val comfortZoneSize = SplitDistance(
            horizontal = spider.gait.comfortZone.horizontal,
            vertical = spider.gait.comfortZone.vertical + (spider.position.y - restPosition.y).coerceAtLeast(.0)
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
        previousEndEffector = endEffector.clone()

        val gait = spider.gait
        var didStep = false

        timeSinceBeginMove += 1
        timeSinceStopMove += 1

        // update target
        val ground = locateGround()
        groundPosition = locateGround()?.position

        if (isDisabled) {
            target = disabledTarget()
        } else {
            if (ground != null) target = ground

            if (!target.isGrounded || !comfortZone.contains(target.position)) {
                target = strandedTarget()
            }
        }

        // inherit parent velocity
        if (!isGrounded()) {
            endEffector.add(spider.velocity)
            endEffector.rotateAroundY(spider.rotationalVelocity.y.toDouble(), spider.position)
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
            val hDistance = endEffector.horizontalDistance(target.position)
            if (hDistance > gait.legDropDistance) {
                endEffector.y = endEffector.y.moveTowards(targetY, legMoveSpeed)
            }

            if (endEffector.distance(target.position) < 0.0001) {
                isMoving = false

                touchingGround = touchingGround()
                didStep = touchingGround
            }

        } else {
            canMove = spider.gait.type.canMoveLeg(this)

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

        if (spider.gait.straightenLegs) {
            val direction = endEffector.clone().subtract(attachmentPosition)
            val orientation = Quaternionf().rotationTo(FORWARD_VECTOR.toVector3f(), direction.toVector3f())

            orientation.stripRelativeZ(spider.gait.legChainPivotMode.get(spider))
            orientation.rotateX(spider.gait.legStraightenRotation)

            chain.straightenDirection(orientation)
        }

        if (!spider.options.debug.disableFabrik) {
            chain.fabrik(endEffector)

            // the spider might be falling while the leg is still grounded
//            if (endEffector.distance(chain.getEndEffector()) > .3) {
//                endEffector.copy(chain.getEndEffector())
//
//                if (!isMoving) {
//                    println("Updated end effector")
//                    isMoving = true
////                    timeSinceBeginMove = 0
//                }
//
//            }
        }

        return chain
    }

    private fun touchingGround(): Boolean {
        return spider.world.isOnGround(endEffector, DOWN_VECTOR.rotate(spider.orientation))
    }

    private fun lookAheadPosition(restPosition: Vector, triggerZoneRadius: Double): Vector {
        if (!spider.isWalking) return restPosition

        val direction = if (spider.velocity.isZero) spider.forwardDirection() else spider.velocity.clone().normalize()

        val lookAhead = direction.multiply(triggerZoneRadius * spider.gait.legLookAheadFraction).add(restPosition)
        lookAhead.rotateAroundY(spider.rotationalVelocity.y.toDouble(), spider.position)
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
        val upVector = UP_VECTOR.rotate(spider.orientation)

        val target = strandedTarget()
        target.position.add(upVector.clone().multiply(spider.lerpedGait.bodyHeight * .5))
        target.position.y = target.position.y.coerceAtLeast((groundPosition?.y ?: - Double.MAX_VALUE) + spider.lerpedGait.bodyHeight * .1)

        return target
    }
}

class LegTarget(
    val position: Vector,
    val isGrounded: Boolean,
    val id: Int,
)