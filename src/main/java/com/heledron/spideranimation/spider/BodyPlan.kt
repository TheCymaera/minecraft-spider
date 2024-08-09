package com.heledron.spideranimation.spider

import org.bukkit.util.Vector

class LegPlan(
    var attachmentPosition: Vector,
    var restPosition: Vector,
    var segments: List<SegmentPlan>,
)

class SegmentPlan(
    var length: Double
) {
    companion object {
        fun equalLength(count: Int, length: Double) = List(count) { SegmentPlan(length) }
    }
}

class BodyPlan {
    var storedScale: Double = 1.0
    var legs: List<LegPlan> = emptyList()

    var straightenLegs = true
    var legStraightenRotation = -60.0
    var renderSegmentThickness = 1.0

    fun addPair(rootX: Double, rootZ: Double, restX: Double, restZ: Double, segmentCount: Int, segmentLength: Double) {
        legs += LegPlan(Vector(rootX, 0.0, rootZ), Vector(restX, 0.0, restZ), SegmentPlan.equalLength(segmentCount, segmentLength))
        legs += LegPlan(Vector(-rootX, 0.0, rootZ), Vector(-restX, 0.0, restZ), SegmentPlan.equalLength(segmentCount, segmentLength))
    }

    fun scale(scale: Double) {
        this.storedScale *= scale
        legs.forEach {
            it.attachmentPosition.multiply(scale)
            it.restPosition.multiply(scale)
            it.segments.forEach { segment ->
                segment.length *= scale
            }
        }
    }
}

fun bipedBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addPair(.0, .0, 1.0, .0, segmentCount, 1.0 * segmentLength)
    }
}

fun quadrupedBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addPair(.0, .0, 0.9, 0.9, segmentCount, 0.9 * segmentLength)
        addPair(.0, .0, 1.0, -1.1, segmentCount, 1.2 * segmentLength)
    }
}

fun hexapodBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addPair(.0, 0.1, 1.0, 1.1, segmentCount, 1.1 * segmentLength)
        addPair(.0, 0.0, 1.3, -0.3, segmentCount, 1.1 * segmentLength)
        addPair(.0, -.1, 1.2, -2.0, segmentCount, 1.6 * segmentLength)
    }
}

fun octopodBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addPair(.0, 0.1, 1.0, 1.6, segmentCount, 1.1 * segmentLength)
        addPair(.0, 0.0, 1.3, 0.4, segmentCount, 1.0 * segmentLength)
        addPair(.0, -.1, 1.3, -0.9, segmentCount, 1.1 * segmentLength)
        addPair(.0, -.2, 1.1, -2.5, segmentCount, 1.6 * segmentLength)
    }
}