package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.spider.configuration.SegmentPlan
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import org.bukkit.Material
import org.bukkit.util.Vector


private fun equalLength(segmentCount: Int, length: Double): List<SegmentPlan> {
    return List(segmentCount) { SegmentPlan(length, FORWARD_VECTOR) }
}

private fun BodyPlan.addLegPair(root: Vector, rest: Vector, segments: List<SegmentPlan>) {
    legs += LegPlan(Vector( root.x, root.y, root.z), Vector( rest.x, rest.y, rest.z), segments)
    legs += LegPlan(Vector(-root.x, root.y, root.z), Vector(-rest.x, rest.y, rest.z), segments.map { it.clone() })
}

fun biped(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.addLegPair(Vector(.0, .0, .0), Vector(1.0, .0, .0), equalLength(segmentCount, 1.0 * segmentLength))
    applyLineLegModel(options.bodyPlan, Material.NETHERITE_BLOCK.createBlockData())
    return options
}

fun quadruped(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.addLegPair(Vector(.0, .0, .0), Vector(0.9,.0, 0.9), equalLength(segmentCount, 0.9 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0, .0, .0), Vector(1.0, .0, -1.1), equalLength(segmentCount, 1.2 * segmentLength))
    applyLineLegModel(options.bodyPlan, Material.NETHERITE_BLOCK.createBlockData())
    return options
}

fun hexapod(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.addLegPair(Vector(.0,.0,0.1), Vector(1.0,.0, 1.1), equalLength(segmentCount, 1.1 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0,.0,0.0), Vector(1.3,.0,-0.3), equalLength(segmentCount, 1.1 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0,.0,-.1), Vector(1.2,.0,-2.0), equalLength(segmentCount, 1.6 * segmentLength))
    applyLineLegModel(options.bodyPlan, Material.NETHERITE_BLOCK.createBlockData())
    return options
}

fun octopod(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.addLegPair(Vector(.0,.0,  .1), Vector(1.0, .0,  1.6), equalLength(segmentCount, 1.1 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0,.0,  .0), Vector(1.3, .0,  0.4), equalLength(segmentCount, 1.0 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0,.0, -.1), Vector(1.3, .0, -0.9), equalLength(segmentCount, 1.1 * segmentLength))
    options.bodyPlan.addLegPair(Vector(.0,.0, -.2), Vector(1.1, .0, -2.5), equalLength(segmentCount, 1.6 * segmentLength))
    applyLineLegModel(options.bodyPlan, Material.NETHERITE_BLOCK.createBlockData())
    return options
}


private fun createRobotSegments(segmentCount: Int, lengthScale: Double) = List(segmentCount) { index ->
    var length = lengthScale.toFloat()
    var initDirection = FORWARD_VECTOR

    if (index == 0) {
        length *= .5f
        initDirection = initDirection.rotateAroundX(Math.PI / 3)
    }

    if (index == 1) length *= .8f

    SegmentPlan(length.toDouble(), initDirection)
}


fun quadBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.bodyModel = SpiderTorsoModels.FLAT.model.clone()
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .2), rest = Vector(1.3 * 1.0,.0, 1.0), createRobotSegments(segmentCount, .9 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15,-.2), rest = Vector(1.3 * 1.1,.0,-1.2), createRobotSegments(segmentCount, 1.2 * .7 * segmentLength))
    applyMechanicalLegModel(options.bodyPlan)
    return options
}

fun hexBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.bodyModel = SpiderTorsoModels.FLAT.model.clone()
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .2), rest = Vector(1.3 * 1.0,.0, 1.3), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .0), rest = Vector(1.3 * 1.2,.0,-0.1), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15,-.2), rest = Vector(1.3 * 1.1,.0,-1.6), createRobotSegments(segmentCount, 1.3 * .7 * segmentLength))
    applyMechanicalLegModel(options.bodyPlan)
    return options
}

fun octoBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.bodyModel = SpiderTorsoModels.FLAT.model.clone()
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .3), rest = Vector(1.3 * 1.0,.0, 1.3), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .1), rest = Vector(1.3 * 1.2,.0, 0.5), createRobotSegments(segmentCount, 1.0 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15, .1), rest = Vector(1.3 * 1.2,.0,-0.7), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    options.bodyPlan.addLegPair(root = Vector(.2,-.2 - .15,-.3), rest = Vector(1.3 * 1.1,.0,-1.6), createRobotSegments(segmentCount, 1.3 * .7 * segmentLength))
    applyMechanicalLegModel(options.bodyPlan)
    return options
}