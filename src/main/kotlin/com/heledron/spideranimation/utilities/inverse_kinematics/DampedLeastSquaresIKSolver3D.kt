package com.heledron.spideranimation.utilities.inverse_kinematics

enum class JointParameter { Angle, Azimuth, Swing, Twist }

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
                descriptors += JointParameterDescriptor(index, JointParameter.Azimuth)
                descriptors += JointParameterDescriptor(index, JointParameter.Swing)
                descriptors += JointParameterDescriptor(index, JointParameter.Twist)
            }
        }
    }

    return descriptors
}

/** Returns the delta actually applied, which may be less than requested after clamping. */
private fun perturbParameter(
    chain: IKChain3D,
    pose: IKChainPose3D,
    descriptor: JointParameterDescriptor,
    delta: Double,
): Double {
    val joint = chain.segments[descriptor.jointIndex].joint
    val jointPose = pose.segments[descriptor.jointIndex]

    if (joint is IKHingeJoint3D && jointPose is IKHingeJointState3D) {
        val nextAngle = (jointPose.angle + delta).coerceIn(joint.minAngle, joint.maxAngle)
        val appliedDelta = nextAngle - jointPose.angle
        jointPose.angle = nextAngle
        return appliedDelta
    }

    if (joint is IKSwingTwistJoint3D && jointPose is IKSwingTwistJointState3D) {
        when (descriptor.parameter) {
            JointParameter.Azimuth -> {
                jointPose.azimuth = wrapRadians(jointPose.azimuth + delta)
                return delta
            }

            JointParameter.Swing -> {
                val nextSwing = (jointPose.swing + delta).coerceIn(0.0, joint.maxSwing)
                val appliedDelta = nextSwing - jointPose.swing
                jointPose.swing = nextSwing
                return appliedDelta
            }

            JointParameter.Twist -> {
                val nextTwist = (jointPose.twist + delta).coerceIn(joint.minTwist, joint.maxTwist)
                val appliedDelta = nextTwist - jointPose.twist
                jointPose.twist = nextTwist
                return appliedDelta
            }

            JointParameter.Angle -> Unit
        }
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
        val descriptor = descriptors[index]
        val joint = chain.segments[descriptor.jointIndex].joint
        val jointPose = pose.segments[descriptor.jointIndex]
        val delta = step[index] * scale

        if (joint is IKHingeJoint3D && jointPose is IKHingeJointState3D) {
            jointPose.angle = (jointPose.angle + delta).coerceIn(joint.minAngle, joint.maxAngle)
            continue
        }

        if (joint is IKSwingTwistJoint3D && jointPose is IKSwingTwistJointState3D) {
            when (descriptor.parameter) {
                JointParameter.Azimuth -> jointPose.azimuth = wrapRadians(jointPose.azimuth + delta)
                JointParameter.Swing -> jointPose.swing = (jointPose.swing + delta).coerceIn(0.0, joint.maxSwing)
                JointParameter.Twist -> jointPose.twist = (jointPose.twist + delta).coerceIn(joint.minTwist, joint.maxTwist)
                JointParameter.Angle -> Unit
            }
        }
    }
}
