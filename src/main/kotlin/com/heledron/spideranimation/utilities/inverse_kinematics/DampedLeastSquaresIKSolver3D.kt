package com.heledron.spideranimation.utilities.inverse_kinematics

enum class JointParameter { Angle, SwingX, SwingY, Twist }

class JointParameterDescriptor(
    val jointIndex: Int,
    val parameter: JointParameter,
)

fun createDampedLeastSquaresIKSolver3D(
    evaluator: IKEvaluator3D = createIKEvaluator3D(),
    options: DampedLeastSquaresOptions = DampedLeastSquaresOptions(),
): IKSolver3D = { chain, pose, target ->
    solve(chain, pose, target, evaluator, options)
}

private fun solve(
    chain: IKChain3D,
    pose: IKChainPose3D,
    ikTarget: IKTarget3D,
    evaluator: IKEvaluator3D,
    options: DampedLeastSquaresOptions,
) {
    val problem = object : LeastSquaresProblem<IKChainPose3D, JointParameterDescriptor, IKEvaluation3D> {
        override fun cloneState(state: IKChainPose3D) = state.clone()

        override fun copyState(destination: IKChainPose3D, source: IKChainPose3D) = destination.copy(source)

        override fun evaluateState(state: IKChainPose3D) = evaluator(chain, state, ikTarget)

        override fun listParameters(state: IKChainPose3D) = createParameterDescriptors(state.segments)

        override fun perturbParameter(state: IKChainPose3D, parameter: JointParameterDescriptor, delta: Double) =
            perturbParameter(chain, state, parameter, delta)

        override fun clampDelta(state: IKChainPose3D, parameter: JointParameterDescriptor, delta: Double) =
            clampDelta(chain, state, parameter, delta)

        override fun applyStep(
            state: IKChainPose3D,
            parameters: List<JointParameterDescriptor>,
            step: DoubleArray,
            scale: Double,
        ) = applyStep(chain, state, parameters, step, scale)
    }

    solveDampedLeastSquares(pose, problem, options)
}

private fun createParameterDescriptors(jointStates: List<IKJointPose3D>): List<JointParameterDescriptor> {
    val descriptors = mutableListOf<JointParameterDescriptor>()

    for (index in jointStates.indices) {
        when (jointStates[index]) {
            is IKHingeJointState3D -> {
                descriptors += JointParameterDescriptor(index, JointParameter.Angle)
            }

            is IKSwingTwistJointState3D -> {
                descriptors += JointParameterDescriptor(index, JointParameter.SwingX)
                descriptors += JointParameterDescriptor(index, JointParameter.SwingY)
                descriptors += JointParameterDescriptor(index, JointParameter.Twist)
            }
        }
    }

    return descriptors
}

private fun perturbParameter(
    chain: IKChain3D,
    pose: IKChainPose3D,
    descriptor: JointParameterDescriptor,
    delta: Double,
): Double {
    val appliedDelta = clampDelta(chain, pose, descriptor, delta)
    applyDelta(chain, pose, descriptor, appliedDelta)
    return appliedDelta
}

private fun clampDelta(
    chain: IKChain3D,
    pose: IKChainPose3D,
    descriptor: JointParameterDescriptor,
    delta: Double,
): Double {
    val joint = chain.segments[descriptor.jointIndex].joint
    val jointPose = pose.segments[descriptor.jointIndex]

    if (joint is IKHingeJoint3D && jointPose is IKHingeJointState3D) {
        return (jointPose.angle + delta).coerceIn(joint.minAngle, joint.maxAngle) - jointPose.angle
    }

    if (joint is IKSwingTwistJoint3D && jointPose is IKSwingTwistJointState3D) {
        return when (descriptor.parameter) {
            // The swing is limited to a cone, so each component's range depends on the other.
            JointParameter.SwingX -> {
                val limit = swingComponentLimit(jointPose.swingY, joint.maxSwing)
                (jointPose.swingX + delta).coerceIn(-limit, limit) - jointPose.swingX
            }

            JointParameter.SwingY -> {
                val limit = swingComponentLimit(jointPose.swingX, joint.maxSwing)
                (jointPose.swingY + delta).coerceIn(-limit, limit) - jointPose.swingY
            }

            JointParameter.Twist -> (jointPose.twist + delta).coerceIn(joint.minTwist, joint.maxTwist) - jointPose.twist

            JointParameter.Angle -> 0.0
        }
    }

    throw IllegalStateException("IKSolver requires matching topology.")
}

private fun applyDelta(
    chain: IKChain3D,
    pose: IKChainPose3D,
    descriptor: JointParameterDescriptor,
    delta: Double,
) {
    val joint = chain.segments[descriptor.jointIndex].joint
    val jointPose = pose.segments[descriptor.jointIndex]

    if (joint is IKHingeJoint3D && jointPose is IKHingeJointState3D) {
        jointPose.angle += delta
        return
    }

    if (joint is IKSwingTwistJoint3D && jointPose is IKSwingTwistJointState3D) {
        when (descriptor.parameter) {
            JointParameter.SwingX -> jointPose.swingX += delta
            JointParameter.SwingY -> jointPose.swingY += delta
            JointParameter.Twist -> jointPose.twist += delta
            JointParameter.Angle -> Unit
        }
        return
    }

    throw IllegalStateException("IKSolver requires matching topology.")
}

private fun applyStep(
    chain: IKChain3D,
    pose: IKChainPose3D,
    descriptors: List<JointParameterDescriptor>,
    step: DoubleArray,
    scale: Double,
) {
    for (index in descriptors.indices) {
        applyDelta(chain, pose, descriptors[index], clampDelta(chain, pose, descriptors[index], step[index] * scale))
    }
}
