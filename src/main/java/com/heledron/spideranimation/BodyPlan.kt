package com.heledron.spideranimation

import com.heledron.spideranimation.components.Leg
import org.bukkit.util.Vector

interface SpiderBodyPlan {
    val scale: Double
    val legs: List<LegPlan>
    fun initialize(spider: Spider)
    fun canMoveLeg(leg: Leg): Boolean
    fun legsInPolygonalOrder(): List<Leg>
    fun legsInUpdateOrder(): List<Leg>
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

class SymmetricalBodyPlan(override val scale: Double, override val legs: List<LegPlan>): SpiderBodyPlan {
    lateinit var spider: Spider

    private var legsInPolygonalOrder = listOf<Leg>()
    private var legsInUpdateOrder = listOf<Leg>()

    override fun initialize(spider: Spider) {
        this.spider = spider

        val diagonals1 = arrayListOf<Leg>()
        val diagonals2 = arrayListOf<Leg>()

        val lefts = arrayListOf<Leg>()
        val rights = arrayListOf<Leg>()
        for (i in spider.body.legs.indices step 2) {
            val left = spider.body.legs[i]
            val right = spider.body.legs[i + 1]
            lefts.add(left)
            rights.add(right)

            if (i % 4 == 0) {
                diagonals1.add(left)
                diagonals2.add(right)
            } else {
                diagonals2.add(left)
                diagonals1.add(right)
            }
        }

        legsInPolygonalOrder = lefts + rights.reversed()
        legsInUpdateOrder = diagonals1 + diagonals2
    }

    override fun legsInPolygonalOrder(): List<Leg> {
        return legsInPolygonalOrder
    }

    override fun legsInUpdateOrder(): List<Leg> {
        return legsInUpdateOrder
    }

    override fun canMoveLeg(leg: Leg): Boolean {
        if (leg.target.isGrounded) return true

        val cooldownLegs: List<Leg>
        if (spider.isGalloping) {
            // always move if uncomfortable
            if (leg.uncomfortable) return true

            cooldownLegs = listOf(horizontal(leg))
        } else {
            // only move when the adjacent legs are grounded
            for (adjacent in adjacent(leg)) {
                if (adjacent.isMoving) return false
            }

            cooldownLegs = diagonal(leg)
        }

        // cooldown
        for (opposite in cooldownLegs) {
            if (opposite.isMoving && !opposite.target.isGrounded && opposite.moveTime < spider.gait.legMoveCooldown) {
                return false
            }
        }

        return true
    }

    // x .
    // . x
    // x .
    private fun horizontal(leg: Leg): Leg {
        val index = spider.body.legs.indexOf(leg)
        val out = spider.body.legs.getOrNull(index + if (index % 2 == 0)  1 else -1)
        return out ?: leg
    }

    private fun diagonal(leg: Leg): List<Leg> {
        val index = spider.body.legs.indexOf(leg)
        val (front, back) = if (index % 2 == 0)  -1 to 3 else -3 to 1
        return listOfNotNull(spider.body.legs.getOrNull(index + front), spider.body.legs.getOrNull(index + back))
    }

    private fun adjacent(leg: Leg): List<Leg> {
        val pair = horizontal(leg)
        return diagonal(pair) + pair
    }
}

class SymmetricalBodyPlanBuilder(var scale: Double = 1.0, var legs: MutableList<LegPlan> = arrayListOf()) {
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
        this.scale *= scale
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

    fun create(): SpiderBodyPlan {
        return SymmetricalBodyPlan(scale, legs)
    }
}

fun quadripedBodyPlan(segmentLength: Double, segmentCount: Int): SymmetricalBodyPlanBuilder {
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