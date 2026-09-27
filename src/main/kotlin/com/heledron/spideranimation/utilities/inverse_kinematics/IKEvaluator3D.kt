package com.heledron.spideranimation.utilities.inverse_kinematics

import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

private const val EPSILON = 0.000001

class IKEvaluation3D(
    val positionError: Double,
    val orientationError: Double,
    override val score: Double,
    override val isSolved: Boolean,
    override val errorVector: DoubleArray,
) : DampedLeastSquaresMetrics

typealias IKEvaluator3D = (chain: IKChain3D, pose: IKChainPose3D, target: IKTarget3D) -> IKEvaluation3D

fun createIKEvaluator3D(
    tolerance: Double = 0.001,
    orientationTolerance: Double = tolerance,
    positionWeight: Double = 1.0,
    orientationWeight: Double = 0.35,
): IKEvaluator3D = { chain, pose, target ->
    evaluateIK(chain, pose, target, tolerance, orientationTolerance, positionWeight, orientationWeight)
}

private fun evaluateIK(
    chain: IKChain3D,
    pose: IKChainPose3D,
    target: IKTarget3D,
    tolerance: Double,
    orientationTolerance: Double,
    positionWeight: Double,
    orientationWeight: Double,
): IKEvaluation3D {
    val worldNodes = chain.getWorldNodes(pose)
    val endEffector = worldNodes.last()

    val positionErrorVector = target.position.clone().subtract(endEffector.position)
    val positionError = positionErrorVector.length()

    val errorVector = mutableListOf(
        positionErrorVector.x * positionWeight,
        positionErrorVector.y * positionWeight,
        positionErrorVector.z * positionWeight,
    )

    var orientationError = 0.0
    val targetOrientation = target.orientation
    if (targetOrientation != null) {
        val orientationErrorVector = rotationErrorVector(endEffector.worldRotation, targetOrientation)
        orientationError = orientationErrorVector.length()

        errorVector += listOf(
            orientationErrorVector.x * orientationWeight,
            orientationErrorVector.y * orientationWeight,
            orientationErrorVector.z * orientationWeight,
        )
    }

    val isSolved = positionError <= tolerance &&
        (targetOrientation == null || orientationError <= orientationTolerance)

    var score = 0.0
    for (value in errorVector) score += value * value

    return IKEvaluation3D(
        positionError = positionError,
        orientationError = orientationError,
        score = score,
        isSolved = isSolved,
        errorVector = errorVector.toDoubleArray(),
    )
}

private fun rotationErrorVector(currentRotation: Quaternionf, targetRotation: Quaternionf): Vector {
    val inverseCurrent = Quaternionf(currentRotation).invert()
    val delta = Quaternionf(targetRotation).mul(inverseCurrent).normalize()

    if (delta.w < 0f) {
        delta.x = -delta.x
        delta.y = -delta.y
        delta.z = -delta.z
        delta.w = -delta.w
    }

    val angle = 2 * acos(delta.w.coerceIn(-1f, 1f))
    val sine = sqrt(max(1f - delta.w * delta.w, 0f))

    if (sine <= EPSILON || angle <= EPSILON) {
        return Vector(delta.x.toDouble(), delta.y.toDouble(), delta.z.toDouble()).multiply(2.0)
    }

    return Vector(
        (delta.x / sine).toDouble(),
        (delta.y / sine).toDouble(),
        (delta.z / sine).toDouble(),
    ).multiply(angle)
}
