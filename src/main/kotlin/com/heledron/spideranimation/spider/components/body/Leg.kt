package com.heledron.spideranimation.spider.components.body

import com.heledron.spideranimation.utilities.ChainSegment
import com.heledron.spideranimation.utilities.KinematicChain
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.isOnGround
import com.heledron.spideranimation.utilities.raycastGround
import com.heledron.spideranimation.utilities.resolveCollision
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.moveTowards
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.ceil
import kotlin.math.floor


class LegStepEvent(val entity: ECSEntity, val spider: SpiderBody, val leg: Leg)

class Leg(
    val ecs: ECS,
    val entity: ECSEntity,
    val spider: SpiderBody,
    var legPlan: LegPlan
) {
    // memo
    lateinit var triggerZone: Capsule; private set
    lateinit var comfortZone: Capsule; private set

    lateinit var restPosition: Vector; private set
    lateinit var lookAheadPosition: Vector; private set
    lateinit var scanLine: LineSegment; private set

    lateinit var attachmentPosition: Vector; private set

    init {
        updateMemo()
    }

    // state
    var groundTarget = locateGroundTarget(); private set
    var target = groundTarget ?: strandedTarget()
    var endEffector = target.position.clone()
    var previousEndEffector = endEffector.clone()
    var chain = run {
        var stride = 0.0
        KinematicChain(attachmentPosition, legPlan.segments.map {
            stride += it.length
            val position = spider.position.clone().add(legPlan.restPosition.clone().normalize().multiply(stride))
            ChainSegment(position, it.length, it.initDirection)
        })
    }

    var touchingGround = true; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set
    var timeSinceStopMove = 0; private set

    private var stepStart = Vector(0, 0, 0)
    private var stepProgress = 0.0 // separate from timeSinceBeginMove since progress can soft-reset on re-target

    var isDisabled = false
    var isPrimary = false
    var canMove = false

    // utils
    val isOutsideTriggerZone: Boolean; get () { return !triggerZone.contains(endEffector) }
    val isUncomfortable: Boolean; get () { return !comfortZone.contains(endEffector) }

    fun isGrounded(): Boolean {
        return touchingGround && !isMoving && !isDisabled
    }

    fun updateMemo() {
        val lerpedGait = spider.lerpedGait()
        val scanOrientation = spider.gait.scanPivotMode.get(spider)

        val upVector = UP_VECTOR.rotate(scanOrientation)

        // rest position
        restPosition = legPlan.restPosition.clone()
        restPosition.add(upVector.clone().multiply(-lerpedGait.bodyHeight))
        restPosition.rotate(scanOrientation).add(spider.position)

        // lookahead
        lookAheadPosition = lookAheadPosition(restPosition, lerpedGait.triggerZoneRadius)

        // scan (from lookahead position)
        val scanStartAxis = upVector.clone().multiply(lerpedGait.bodyHeight * 1.6)
        val scanAxis = upVector.clone().multiply(-lerpedGait.bodyHeight * 2.5)
        scanLine = LineSegment.fromOffset(lookAheadPosition.clone().add(scanStartAxis), scanAxis)

        // trigger/comfort zone capsules (from rest position; same axis as scan)
        val zoneStart = restPosition.clone().add(scanStartAxis)
        val zoneEnd = zoneStart.clone().add(scanAxis)
        triggerZone = Capsule(zoneStart, zoneEnd, lerpedGait.triggerZoneRadius)
        comfortZone = Capsule(zoneStart, zoneEnd, spider.gait.comfortZoneRadius)

        // attachment position
        attachmentPosition = legPlan.attachmentPosition.clone().rotate(spider.orientation).add(spider.position)
    }

    fun update() {
        updateMovement()

        // update chain
        chain.root.copy(attachmentPosition)

        if (spider.gait.straightenLegs) {
            val pivot = Quaternionf(spider.gait.legChainPivotMode.get(spider))

            val direction = endEffector.clone().subtract(attachmentPosition)
            val rotation = direction.getRotationAroundAxis(pivot)

            rotation.x += spider.gait.legStraightenRotation
            val orientation = pivot.rotateYXZ(rotation.y, rotation.x, .0f)

            chain.straightenDirection(orientation)
        }

        if (!spider.debug.disableFabrik) {
            chain.fabrik(endEffector)
        }
    }

    private fun updateMovement() {
        previousEndEffector = endEffector.clone()

        timeSinceBeginMove += 1
        timeSinceStopMove += 1

        // update target
        val oldTargetPosition = target.position.clone()
        val ground = locateGroundTarget()
        groundTarget = ground

        if (isDisabled) {
            target = disabledTarget(ground?.position)
        } else {
            if (ground != null) target = ground

            if (!target.isGrounded || !comfortZone.contains(target.position)) {
                target = strandedTarget()
            }
        }

        // reset step if target has changed
        val targetChanged = oldTargetPosition.distanceSquared(target.position) > 1e-6
        if (targetChanged) {
            softResetStep()
        }

        // inherit parent motion while free
        if (!isGrounded()) {
            applyBodyMotion(endEffector)
            if (isMoving) applyBodyMotion(stepStart)
        }

        var didStep = false
        if (isMoving) {
            didStep = updateMove()
        } else {
            canMove = spider.gait.type.canMoveLeg(this)
            if (canMove) beginMove()
        }


        // resolve ground collision
        val collision = spider.world.resolveCollision(endEffector, DOWN_VECTOR)
        if (collision != null) {
            // ignore collision if it would push the end effector further from the target
            // e.g. when grazing an obstacle
            val isFurtherFromTarget = collision.position.distance(target.position) > endEffector.distance(target.position)
            if (!isFurtherFromTarget) {
                didStep = true
                touchingGround = true
                endEffector.y = collision.position.y
            }
        }

        if (didStep) ecs.emit(LegStepEvent(entity = entity, spider = spider, leg = this))
    }

    private fun applyBodyMotion(point: Vector) {
        point.add(spider.velocity)
        point.rotateAroundY(spider.rotationalVelocity.y.toDouble(), spider.position)
    }

    private fun beginMove() {
        isMoving = true
        timeSinceBeginMove = 0
        stepStart = endEffector.clone()
        stepProgress = 0.0
        touchingGround = false
    }

    private fun completeMove(): Boolean {
        isMoving = false
        timeSinceStopMove = 0
        endEffector.copy(target.position)
        touchingGround = touchingGround()
        return touchingGround
    }

    /** Parabolic height envelope: 0 at ends, 1 at mid-step. */
    private fun stepLiftFactor(t: Double) = 4.0 * t * (1.0 - t)

    private fun sampleStepPosition(t: Double): Vector {
        val position = Vector(
            stepStart.x.lerp(target.position.x, t),
            stepStart.y.lerp(target.position.y, t),
            stepStart.z.lerp(target.position.z, t),
        )
        position.y += spider.gait.legLiftHeight * stepLiftFactor(t)
        return position
    }

    private fun updateMove(): Boolean {
		val gait = spider.gait
    	
        val distance = stepStart.horizontalDistance(target.position).coerceAtLeast(1e-4)
        stepProgress = stepProgress.moveTowards(1.0, gait.legMoveSpeed / distance)

        endEffector.copy(sampleStepPosition(stepProgress))

        if (stepProgress >= 1.0) {
            return completeMove()
        }

        return false
    }

    private fun softResetStep() {
        stepStart = endEffector.clone()
        stepProgress = 0.0
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

    fun locateGroundTarget(): LegTarget? {
        val lookAhead = lookAheadPosition.toLocation(spider.world)
        val scanStartPosition = scanLine.point1
        val scanVector = scanLine.vector()
        val scanLength = scanVector.length()

        fun candidateAllowed(id: Int): Boolean {
            return true
        }

        var id = 0
        val world = spider.world
        fun rayCast(x: Double, z: Double): LegTarget? {
            id += 1

            if (!candidateAllowed(id)) return null

            val start = Vector(x, scanStartPosition.y, z)
            val hit = world.raycastGround(start, scanVector, scanLength) ?: return null

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

    private fun disabledTarget(groundPosition: Vector?): LegTarget {
        val lerpedGait = spider.lerpedGait()
        val upVector = UP_VECTOR.rotate(spider.orientation)

        val target = strandedTarget()
        target.position.add(upVector.clone().multiply(lerpedGait.bodyHeight * .5))

        val minY = (groundPosition?.y ?: -Double.MAX_VALUE) + lerpedGait.bodyHeight * .1
        target.position.y = target.position.y.coerceAtLeast(minY)

        return target
    }
}

class LegTarget(
    val position: Vector,
    val isGrounded: Boolean,
    val id: Int,
)