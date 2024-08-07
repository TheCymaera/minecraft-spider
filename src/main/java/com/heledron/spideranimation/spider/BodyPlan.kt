package com.heledron.spideranimation.spider

import com.heledron.spideranimation.*
import com.heledron.spideranimation.spider.LegLookUp.isDiagonal1
import com.heledron.spideranimation.spider.LegLookUp.isDiagonal2
import com.heledron.spideranimation.spider.LegLookUp.isLeftLeg
import com.heledron.spideranimation.spider.LegLookUp.isRightLeg
import org.bukkit.util.Vector
import org.joml.Vector2d

interface SpiderBodyPlan {
    val legs: List<LegPlan>
    fun getLegsInUpdateOrder(spider: Spider): List<Leg>
    fun getNormal(spider: Spider): NormalInfo?
}

class LegPlan(
    val attachmentPosition: Vector,
    val restPosition: Vector,
    val segmentLength: Double,
)


class NormalInfo(
    val normal: Vector,
    val origin: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null
)


class SymmetricalBodyPlan(override val legs: List<LegPlan>): SpiderBodyPlan {
    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { isLeftLeg(it) }
        val rights = legs.indices.filter { isRightLeg(it) }
        return lefts + rights.reversed()
    }

    private fun legsInUpdateOrder(): List<Int> {
        val diagonal1 = legs.indices.filter { isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { isDiagonal2(it) }
        return diagonal1 + diagonal2
    }

    private var legsInPolygonalOrder = legsInPolygonalOrder()
    private var legsInUpdateOrder = legsInUpdateOrder()

    override fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        return legsInUpdateOrder.map { spider.body.legs[it] }
    }

    override fun getNormal(spider: Spider): NormalInfo? {
        if (spider.gait.useLegacyNormalForce) {
            val pairs = LegLookUp.diagonalPairs(legs.indices.toList())
            if (pairs.any { pair -> pair.mapNotNull { spider.body.legs.getOrNull(it) }.all { it.isGrounded() } }) {
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
}

class SymmetricalBodyPlanBuilder(var legs: MutableList<LegPlan> = arrayListOf()) {
    fun addPair(rootX: Double, rootZ: Double, restX: Double, restZ: Double, segmentLength: Double) {
        legs += LegPlan(Vector(rootX, 0.0, rootZ), Vector(restX, 0.0, restZ), segmentLength)
        legs += LegPlan(Vector(-rootX, 0.0, rootZ), Vector(-restX, 0.0, restZ), segmentLength)
    }

    fun scale(scale: Double) {
//        for (leg in legs) {
//            leg.attachmentPosition.multiply(scale)
//            leg.restPosition.multiply(scale)
//            leg.segmentLength
//        }
        this.legs = legs.map { LegPlan(
            it.attachmentPosition.clone().multiply(scale),
            it.restPosition.clone().multiply(scale),
            it.segmentLength * scale
        ) }.toMutableList()
    }

    fun create(): SymmetricalBodyPlan {
        return SymmetricalBodyPlan(legs)
    }
}

fun quadrupedBodyPlan(): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, .0, 0.9, 0.9, 0.9)
        addPair(.0, .0, 1.0, -1.1, 1.2)
    }
}

fun hexapodBodyPlan(): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, 0.1, 1.0, 1.1, 1.1)
        addPair(.0, 0.0, 1.3, -0.3, 1.1)
        addPair(.0, -.1, 1.2, -2.0, 1.6)
    }
}

fun octopodBodyPlan(): SymmetricalBodyPlanBuilder {
    return SymmetricalBodyPlanBuilder().apply {
        addPair(.0, 0.1, 1.0, 1.6, 1.1)
        addPair(.0, 0.0, 1.3, 0.4, 1.0)
        addPair(.0, -.1, 1.3, -0.9, 1.1)
        addPair(.0, -.2, 1.1, -2.5, 1.6)
    }
}