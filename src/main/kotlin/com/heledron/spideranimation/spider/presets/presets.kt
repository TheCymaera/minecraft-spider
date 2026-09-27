package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.spider.configuration.SegmentPlan
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.DisplayModel
import com.heledron.spideranimation.utilities.inverse_kinematics.IKChain3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKHingeJoint3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKJoint3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKSwingTwistJoint3D
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import org.bukkit.Material
import org.bukkit.util.Vector
import kotlin.math.PI


private const val ROOT_MAX_SWING = 0.3 * PI

private const val ROOT_TWIST = 0.1 * PI


private const val FIRST_KNEE_LEEWAY = 0.1 * PI
private const val KNEE_LEEWAY = 0.8 * PI

private fun hipJoint(): IKJoint3D = IKSwingTwistJoint3D(
    maxSwing = ROOT_MAX_SWING,
    minTwist = -ROOT_TWIST,
    maxTwist = ROOT_TWIST,
    referenceAxis = UP_VECTOR,
)

private fun kneeJoint(leeway: Double = KNEE_LEEWAY, inverted: Boolean = false): IKJoint3D = kneeJoint(
    min = if (inverted) -leeway else 0.0,
    max = if (inverted) 0.0 else leeway,
)

private fun kneeJoint(min: Double, max: Double): IKJoint3D = IKHingeJoint3D(
    minAngle = min,
    maxAngle = max,
    axis = Vector(1, 0, 0),
    reference = IKChain3D.CHAIN_AXIS,
)

private fun equalLength(segmentCount: Int, length: Double): List<SegmentPlan> {
    return List(segmentCount) { index ->
        val joint = if (index == 0) hipJoint() else kneeJoint()
        SegmentPlan(length, joint, DisplayModel.empty())
    }
}

private fun createRobotSegments(segmentCount: Int, lengthScale: Double): List<SegmentPlan> {
    // One extra entry: the zero-length aim root
    return List(segmentCount + 1) { index ->
        when (index) {
            0 -> SegmentPlan(0.0, hipJoint(), DisplayModel.empty())
            1 -> SegmentPlan(lengthScale * .5, kneeJoint(min = FIRST_KNEE_LEEWAY * 1.0, max = FIRST_KNEE_LEEWAY * 2.0), DisplayModel.empty())
            2 -> SegmentPlan(lengthScale * .8, kneeJoint(inverted = true), DisplayModel.empty())
            else -> SegmentPlan(lengthScale, kneeJoint(), DisplayModel.empty())
        }
    }
}

private fun BodyPlan.addLegPair(root: Vector, rest: Vector, segments: List<SegmentPlan>) {
    legs += LegPlan(Vector( root.x, root.y, root.z), Vector( rest.x, rest.y, rest.z), segments)
    legs += LegPlan(Vector(-root.x, root.y, root.z), Vector(-rest.x, rest.y, rest.z), segments.map { it.clone() })
}

fun biped(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan.addLegPair(Vector(.0, .0, .0), Vector(1.0, .0, .0), equalLength(segmentCount, segmentLength))
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