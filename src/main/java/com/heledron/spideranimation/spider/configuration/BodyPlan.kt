package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.ChainSegment
import com.heledron.spideranimation.utilities.FORWARD_VECTOR
import org.bukkit.Material
import org.bukkit.util.Vector

class SegmentPlan(
    var length: Double,
    var thickness: Double,
    var initDirection: Vector,
) {
    fun clone() = SegmentPlan(length, thickness, initDirection.clone())

    companion object {
        fun tapered(segmentCount: Int, segmentLength: Double, rootThickness: Double, tipThickness: Double) = List(segmentCount) {
            val fraction = it.toDouble() / (segmentCount - 1)

            val thickness = rootThickness + (tipThickness - rootThickness) * fraction
            SegmentPlan(segmentLength, thickness, FORWARD_VECTOR)
        }
    }
}

class LegPlan(
    var attachmentPosition: Vector,
    var restPosition: Vector,
    var segments: List<SegmentPlan>,
)

class BodyPlan {
    var scale = 1.0
    var legs = emptyList<LegPlan>()

    var material = Material.NETHERITE_BLOCK
    var straightenLegs = true
    var legStraightenRotation = -80.0

    fun addLegPair(root: Vector, rest: Vector, segments: List<SegmentPlan>) {
        legs += LegPlan(Vector( root.x, root.y, root.z), Vector( rest.x, rest.y, rest.z), segments)
        legs += LegPlan(Vector(-root.x, root.y, root.z), Vector(-rest.x, rest.y, rest.z), segments.map { it.clone() })
    }

    fun scale(scale: Double) {
        this.scale *= scale
        legs.forEach {
            it.attachmentPosition.multiply(scale)
            it.restPosition.multiply(scale)
            it.segments.forEach { segment ->
                segment.length *= scale
                segment.thickness *= scale
            }
        }
    }
}