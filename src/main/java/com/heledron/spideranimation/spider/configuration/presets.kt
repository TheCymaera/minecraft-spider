package com.heledron.spideranimation.spider.configuration

import org.bukkit.util.Vector

private const val rootThickness = 1.0/16 * 4.5
private const val tipThickness = 1.0/16 * 1.5

private fun createTaperedSegments(segmentCount: Int, length: Double): List<SegmentPlan> {
    return SegmentPlan.tapered(segmentCount, length, rootThickness, tipThickness)
}

fun bipedBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {


        addLegPair(Vector(.0, .0, .0), Vector(1.0, .0, .0), createTaperedSegments(segmentCount, 1.0 * segmentLength))
    }
}

fun quadrupedBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addLegPair(Vector(.0, .0, .0), Vector(0.9,.0, 0.9), createTaperedSegments(segmentCount, 0.9 * segmentLength))
        addLegPair(Vector(.0, .0, .0), Vector(1.0, .0, -1.1), createTaperedSegments(segmentCount, 1.2 * segmentLength))
    }
}

fun hexapodBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addLegPair(Vector(.0,.0,0.1), Vector(1.0,.0, 1.1), createTaperedSegments(segmentCount, 1.1 * segmentLength))
        addLegPair(Vector(.0,.0,0.0), Vector(1.3,.0,-0.3), createTaperedSegments(segmentCount, 1.1 * segmentLength))
        addLegPair(Vector(.0,.0,-.1), Vector(1.2,.0,-2.0), createTaperedSegments(segmentCount, 1.6 * segmentLength))
    }
}

fun octopodBodyPlan(segmentCount: Int, segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        addLegPair(Vector(.0,.0,  .1), Vector(1.0, .0,  1.6), createTaperedSegments(segmentCount, 1.1 * segmentLength))
        addLegPair(Vector(.0,.0,  .0), Vector(1.3, .0,  0.4), createTaperedSegments(segmentCount, 1.0 * segmentLength))
        addLegPair(Vector(.0,.0, -.1), Vector(1.3, .0, -0.9), createTaperedSegments(segmentCount, 1.1 * segmentLength))
        addLegPair(Vector(.0,.0, -.2), Vector(1.1, .0, -2.5), createTaperedSegments(segmentCount, 1.6 * segmentLength))
    }
}

fun hexBotBodyPlan(_segmentCount: Int, _segmentLength: Double): BodyPlan {
    return BodyPlan().apply {
        val thickness = 1.0/16 * 2.4

        fun buildSegments(length: Double)  = SegmentPlan.tapered(4, length, thickness, thickness).apply {
            this[0].length *= .5
            this[0].initDirection.rotateAroundX(Math.PI / 3)

            this[1].length *= .8
        }
        addLegPair(root = Vector(.2,-.2 - .15, .2), rest = Vector(1.3 * 1.0,.0, 1.3), buildSegments(1.1 * .7))
        addLegPair(root = Vector(.2,-.2 - .15, .0), rest = Vector(1.3 * 1.2,.0,-0.1), buildSegments(1.1 * .7))
        addLegPair(root = Vector(.2,-.2 - .15,-.2), rest = Vector(1.3 * 1.1,.0,-1.6), buildSegments(1.3 * .7))
    }
}