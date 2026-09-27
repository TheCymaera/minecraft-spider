package com.heledron.spideranimation.spider.components.body

import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.utilities.inverse_kinematics.IKChain3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKChainPose3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKChainSegment3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKChainWorldNode3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKSolver3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKTarget3D
import com.heledron.spideranimation.utilities.inverse_kinematics.createDampedLeastSquaresIKSolver3D
import com.heledron.spideranimation.utilities.inverse_kinematics.createIKEvaluator3D
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.util.Vector
import org.joml.Quaternionf

class SpiderLegIK(
    val plan: LegPlan,
    val chain: IKChain3D,
    val pose: IKChainPose3D,
    private val solver: IKSolver3D,
    private val target: IKTarget3D,
) {
    var nodes: List<IKChainWorldNode3D> = emptyList()
        private set

    val segmentTipPositions: List<Vector>
        get() = nodes.drop(1).map { it.position }

    val segmentModelRotations: List<Quaternionf>
        get() = nodes.drop(1).map { Quaternionf(it.worldRotation) }

    fun attachmentPosition(spiderPosition: Vector, spiderOrientation: Quaternionf): Vector =
        plan.attachmentPosition.clone().rotate(spiderOrientation).add(spiderPosition)

    fun solve(spiderPosition: Vector, spiderOrientation: Quaternionf, endEffector: Vector) {
        pose.position.copy(attachmentPosition(spiderPosition, spiderOrientation))
        pose.orientation.set(spiderOrientation).mul(plan.restOrientation)

        target.position.copy(endEffector)

        solver(chain, pose, target)

        nodes = chain.getWorldNodes(pose)
    }
}

fun createSpiderLegIK(plan: LegPlan): SpiderLegIK {
    val chain = IKChain3D(
        plan.segments.map { IKChainSegment3D(it.length, it.joint) },
    )

    val evaluator = createIKEvaluator3D(tolerance = 0.01)

    return SpiderLegIK(
        plan = plan,
        chain = chain,
        pose = chain.createPose(Vector(0.0, 0.0, 0.0), Quaternionf(plan.restOrientation)),
        solver = createDampedLeastSquaresIKSolver3D(evaluator),
        target = IKTarget3D(Vector(0.0, 0.0, 0.0)),
    )
}
