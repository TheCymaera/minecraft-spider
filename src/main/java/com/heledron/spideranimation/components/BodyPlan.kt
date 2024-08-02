package com.heledron.spideranimation.components

import com.heledron.spideranimation.*
import org.bukkit.util.Vector
import org.joml.Vector2d

interface SpiderBodyPlan {
    val legs: List<LegPlan>
    fun getLegsInUpdateOrder(spider: Spider): List<Leg>
    fun canMoveLeg(spider: Spider, leg: Leg): Boolean
    fun getNormal(spider: Spider): NormalInfo?
}

class LegPlan(
    val attachmentPosition: Vector,
    val restPosition: Vector,
    val segments: List<SegmentPlan>,
)

class SegmentPlan(
    val length: Double,
    val thickness: Double,
)


class NormalInfo(
    val normal: Vector,
    val origin: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null
)

class SymmetricalBodyPlan(override val legs: List<LegPlan>): SpiderBodyPlan {
    private var legsInPolygonalOrder = listOf<Int>()
    private var legsInUpdateOrder = listOf<Int>()

    init {
        val lefts = legs.indices.filter { isLeftLeg(it) }
        val rights = legs.indices.filter { isRightLeg(it) }
        legsInPolygonalOrder = lefts + rights.reversed()

        val diagonal1 = legs.indices.filter { isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { isDiagonal2(it) }
        legsInUpdateOrder = diagonal1 + diagonal2
    }

    override fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        return legsInUpdateOrder.map { spider.body.legs[it] }
    }

    fun isLeftLeg(leg: Int): Boolean {
        return leg % 2 == 0
    }

    fun isRightLeg(leg: Int): Boolean {
        return !isLeftLeg(leg)
    }

    fun getPairIndex(leg: Int): Int {
        return leg / 2
    }

    fun isDiagonal1(leg: Int): Boolean {
        return if (getPairIndex(leg) % 2 == 0) isLeftLeg(leg) else isRightLeg(leg)
    }

    fun isDiagonal2(leg: Int): Boolean {
        return !isDiagonal1(leg)
    }

    fun diagonalFront(leg: Int): Int {
        return if (isLeftLeg(leg)) leg - 1 else leg - 3
    }

    fun diagonalBack(leg: Int): Int {
        return if (isLeftLeg(leg)) leg + 3 else leg + 1
    }

    override fun getNormal(spider: Spider): NormalInfo? {
        if (spider.gait.useLegacyNormalForce) {
            if (spider.body.legs.any { it.isGrounded() && diagonal(spider, it).all { it.isGrounded() } }) {
                return NormalInfo(normal = Vector(0, 1, 0))
            }

            return null
        }

        val centreOfMass = averageVector(spider.body.legs.map { it.endEffector })
        lerpVectorByFactor(centreOfMass, spider.location.toVector(), 0.5)
        centreOfMass.y += 0.01

        val groundedLegs = legsInPolygonalOrder.map { spider.body.legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        fun applyStabilization(normal: NormalInfo) {
            if (normal.origin == null) return
            if (normal.centreOfMass == null) return

            if (horizontalDistance(normal.origin, normal.centreOfMass) < spider.gait.polygonLeeway) {
                normal.origin.x = normal.centreOfMass.x
                normal.origin.z = normal.centreOfMass.z
            }

            val stabilizationTarget = normal.origin.clone().setY(normal.centreOfMass.y)
            lerpVectorByFactor(normal.centreOfMass, stabilizationTarget, spider.gait.stabilizationFactor)

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

    override fun canMoveLeg(spider: Spider, leg: Leg): Boolean {
        fun hasCooldown(leg: Leg, cooldown: Int): Boolean {
            return leg.isMoving && leg.target.isGrounded && leg.timeSinceBeginMove < cooldown
        }


        // always move if the target is not on ground
        if (!leg.target.isGrounded) return true

        if (spider.isGalloping) {
            val index = spider.body.legs.indexOf(leg)
            val pair = horizontal(spider, leg)
            leg.isPrimary = isDiagonal1(index) || pair.isDisabled || !pair.target.isGrounded
            if (leg.isPrimary) {
                // cooldown
                val front = spider.body.legs.getOrNull(diagonalFront(index))
                val back = spider.body.legs.getOrNull(diagonalBack(index))
                if (listOfNotNull(front, back).any { hasCooldown(it, spider.gait.legGallopVerticalCooldown) }) return false

                return leg.isOutsideTriggerZone || !leg.touchingGround
            } else {
                return pair.isMoving && !hasCooldown(pair, spider.gait.legGallopHorizontalCooldown)
            }
        }

        leg.isPrimary = true

        // ensure adjacent legs are grounded
        // ignore if disabled
        // ignore if target is not grounded
        if (adjacent(spider, leg).any { !it.isGrounded() && !it.isDisabled && it.target.isGrounded }) return false

        // cooldown
        if (diagonal(spider, leg).any { hasCooldown(it, spider.gait.legWalkCooldown) }) return false

        val wantsToMove = leg.isOutsideTriggerZone || !leg.touchingGround
        val alreadyAtTarget = leg.endEffector.distanceSquared(leg.target.position) < 0.01
        val atLeastOneLegOnGround = spider.body.legs.any { it.isGrounded() }

        return wantsToMove && !alreadyAtTarget && atLeastOneLegOnGround
    }

    // x .
    // . x
    // x .
    private fun horizontal(spider: Spider, leg: Leg): Leg {
        val index = spider.body.legs.indexOf(leg)
        return spider.body.legs[index + if (index % 2 == 0)  1 else -1]
    }

    private fun diagonal(spider: Spider, leg: Leg): List<Leg> {
        val index = spider.body.legs.indexOf(leg)
        val (front, back) = if (index % 2 == 0)  -1 to 3 else -3 to 1
        return listOfNotNull(spider.body.legs.getOrNull(index + front), spider.body.legs.getOrNull(index + back))
    }

    private fun adjacent(spider: Spider, leg: Leg): List<Leg> {
        val index = spider.body.legs.indexOf(leg)
        val front = spider.body.legs.getOrNull(index - 2)
        val back = spider.body.legs.getOrNull(index + 2)
        val horizontal = spider.body.legs[if (index % 2 == 0) index + 1 else index - 1]
        return listOfNotNull(front, back, horizontal)
    }
}

class SymmetricalBodyPlanBuilder(var legs: MutableList<LegPlan> = arrayListOf()) {
    fun addPair(rootX: Double, rootZ: Double, restX: Double, restZ: Double, segmentLength: Double, segmentCount: Int) {
        val segmentPlan = SegmentPlan(segmentLength, .1)
        legs.add(LegPlan(Vector(rootX, 0.0, rootZ), Vector(restX, 0.0, restZ), List(segmentCount) { segmentPlan }))
        legs.add(LegPlan(Vector(-rootX, 0.0, rootZ), Vector(-restX, 0.0, restZ), List(segmentCount) { segmentPlan }))
    }

    fun autoAssignThickness() {
        val maxThickness = 1.5/16 * 4
        val minThickness = 1.5/16 * 1

        legs = legs.map { legPlan ->
            val segmentCount = legs.first().segments.size
            val segments = legPlan.segments.mapIndexed { i, segmentPlan ->
                val thickness = (segmentCount - i - 1) * (maxThickness - minThickness) / segmentCount + minThickness
                SegmentPlan(segmentPlan.length, thickness)
            }
            LegPlan(legPlan.attachmentPosition, legPlan.restPosition, segments)
        }.toMutableList()
    }

    fun scale(scale: Double) {
        legs = legs.map { legPlan ->
            val attachmentPosition = legPlan.attachmentPosition.clone().multiply(scale)
            val restPosition = legPlan.restPosition.clone().multiply(scale)
            val segments = legPlan.segments.map { SegmentPlan(it.length * scale, it.thickness * scale) }
            LegPlan(attachmentPosition, restPosition, segments)
        }.toMutableList()
    }

//    fun addSpace(left: Double, right: Double, forward: Double, backward: Double) {
//        legs.forEach { legPlan ->
//            val z = .5 * if (legPlan.restPosition.z > 0) forward else -backward
//            val x = .5 * if (legPlan.restPosition.x > 0) left else -right
//            legPlan.attachmentPosition.add(Vector(x, 0.0, z))
//            legPlan.restPosition.add(Vector(x, 0.0, z))
//        }
//    }
//
//    fun liftAttachment(front: Double, back: Double) {
//        legs.forEach { legPlan ->
//            val y = .5 * if (legPlan.restPosition.z > 0) front else back
//            legPlan.attachmentPosition.y += y
//        }
//    }

    fun create(): SymmetricalBodyPlan {
        return SymmetricalBodyPlan(legs)
    }
}

fun quadrupedBodyPlan(segmentLength: Double, segmentCount: Int): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, .0, 0.9, 0.9, 0.9 * segmentLength, segmentCount)
        addPair(.0, .0, 1.0, -1.1, 1.2 * segmentLength, segmentCount)
        autoAssignThickness()
//        addSpace(.5, .5, 1.0, 1.0)
//        liftAttachment(-.1, .3)
    }
}

fun hexapodBodyPlan(segmentLength: Double, segmentCount: Int): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, 0.1, 1.0, 1.1, 1.1 * segmentLength, segmentCount)
        addPair(.0, 0.0, 1.3, -0.3, 1.1 * segmentLength, segmentCount)
        addPair(.0, -.1, 1.2, -2.0, 1.6 * segmentLength, segmentCount)
        autoAssignThickness()
    }
}

fun octopodBodyPlan(segmentLength: Double, segmentCount: Int): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, 0.1, 1.0, 1.6, 1.1 * segmentLength, segmentCount)
        addPair(.0, 0.0, 1.3, 0.4, 1.0 * segmentLength, segmentCount)
        addPair(.0, -.1, 1.3, -0.9, 1.1 * segmentLength, segmentCount)
        addPair(.0, -.2, 1.1, -2.5, 1.6 * segmentLength, segmentCount)
        autoAssignThickness()
    }
}