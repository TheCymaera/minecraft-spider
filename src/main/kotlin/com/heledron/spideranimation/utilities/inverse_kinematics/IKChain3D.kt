package com.heledron.spideranimation.utilities.inverse_kinematics

import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

sealed interface IKJoint3D {
    fun rotation(state: IKJointPose3D): Quaternionf
}

sealed interface IKJointPose3D {
    fun clone(): IKJointPose3D
    fun copy(other: IKJointPose3D)
    fun lerpState(other: IKJointPose3D, t: Double)
}

private const val REST_BIAS = 0.035

private const val REST_AZIMUTH = PI / 2

fun IKJoint3D.restState(): IKJointPose3D = when (this) {
    is IKHingeJoint3D -> IKHingeJointState3D(
        when {
            minAngle >= 0.0 -> minAngle + REST_BIAS
            maxAngle <= 0.0 -> maxAngle - REST_BIAS
            // Straddles zero, so bias positive: any bias will do, but it must not
            // be exactly zero
            else -> REST_BIAS
        }.coerceIn(minAngle, maxAngle),
    )

    is IKSwingTwistJoint3D -> IKSwingTwistJointState3D(azimuth = REST_AZIMUTH)
}

class IKSwingTwistJoint3D(
    val maxSwing: Double,
    val minTwist: Double,
    val maxTwist: Double,
    val referenceAxis: Vector,
) : IKJoint3D {
    init {
        require(minTwist <= maxTwist) { "IKSwingTwistJoint3D.minTwist cannot be greater than maxTwist." }
        require(referenceAxis.lengthSquared() > 0.0) { "IKSwingTwistJoint3D.referenceAxis must be non-zero." }
    }

    override fun rotation(state: IKJointPose3D): Quaternionf {
        val pose = state as? IKSwingTwistJointState3D
            ?: throw IllegalArgumentException("IKSwingTwistJoint3D requires a matching IKSwingTwistJointState3D.")

        val direction = directionFromSpherical(pose.azimuth, pose.swing)
        val swingRotation = quaternionFromTo(IKChain3D.CHAIN_AXIS, direction, referenceAxis)
        val twistRotation = Quaternionf().fromAxisAngleRad(IKChain3D.CHAIN_AXIS.toVector3f(), pose.twist.toFloat())
        return swingRotation.mul(twistRotation).normalize()
    }
}

class IKHingeJoint3D(
    val minAngle: Double,
    val maxAngle: Double,
    val axis: Vector,
    val reference: Vector,
) : IKJoint3D {
    init {
        require(minAngle <= maxAngle) { "IKHingeJoint3D.minAngle cannot be greater than maxAngle." }
        require(axis.lengthSquared() > 0.0) { "IKHingeJoint3D.axis must be non-zero." }
        require(reference.lengthSquared() > 0.0) { "IKHingeJoint3D.reference must be non-zero." }
    }

    override fun rotation(state: IKJointPose3D): Quaternionf {
        val pose = state as? IKHingeJointState3D
            ?: throw IllegalArgumentException("IKHingeJoint3D requires a matching IKHingeJointState3D.")
        return Quaternionf().fromAxisAngleRad(axis.toVector3f(), pose.angle.toFloat())
    }
}

class IKSwingTwistJointState3D(
    var azimuth: Double = 0.0,
    var swing: Double = 0.0,
    var twist: Double = 0.0,
) : IKJointPose3D {
    override fun clone() = IKSwingTwistJointState3D(azimuth, swing, twist)

    override fun copy(other: IKJointPose3D) {
        val source = other as? IKSwingTwistJointState3D
            ?: throw IllegalArgumentException("IKSwingTwistJointState3D.copy requires a matching state.")
        azimuth = source.azimuth
        swing = source.swing
        twist = source.twist
    }

    override fun lerpState(other: IKJointPose3D, t: Double) {
        val target = other as? IKSwingTwistJointState3D
            ?: throw IllegalArgumentException("IKSwingTwistJointState3D.lerpState requires a matching state.")
        azimuth = wrapRadians(azimuth + shortestAngleDelta(azimuth, target.azimuth) * t)
        swing += (target.swing - swing) * t
        twist += (target.twist - twist) * t
    }
}

class IKHingeJointState3D(
    var angle: Double = 0.0,
) : IKJointPose3D {
    override fun clone() = IKHingeJointState3D(angle)

    override fun copy(other: IKJointPose3D) {
        val source = other as? IKHingeJointState3D
            ?: throw IllegalArgumentException("IKHingeJointState3D.copy requires a matching state.")
        angle = source.angle
    }

    override fun lerpState(other: IKJointPose3D, t: Double) {
        val target = other as? IKHingeJointState3D
            ?: throw IllegalArgumentException("IKHingeJointState3D.lerpState requires a matching state.")
        angle += shortestAngleDelta(angle, target.angle) * t
    }
}

class IKChainSegment3D(
    val length: Double,
    val joint: IKJoint3D,
)

class IKChainWorldNode3D(
    val position: Vector,
    val worldRotation: Quaternionf,
    val localRotation: Quaternionf,
)

class IKChain3D(
    val segments: List<IKChainSegment3D>,
) {
    val totalLength: Double get() = segments.sumOf { it.length }

    fun createPose(position: Vector, rotation: Quaternionf): IKChainPose3D {
        return IKChainPose3D(
            position.clone(),
            Quaternionf(rotation),
            segments.map { it.joint.restState() },
        )
    }

    fun getWorldNodes(pose: IKChainPose3D): List<IKChainWorldNode3D> {
        val nodes = mutableListOf(
            IKChainWorldNode3D(pose.position.clone(), Quaternionf(pose.orientation), Quaternionf(pose.orientation)),
        )

        val cursor = pose.position.clone()
        val cursorRotation = Quaternionf(pose.orientation)

        for (index in segments.indices) {
            val segment = segments[index]
            val jointState = pose.segments[index]

            val localRotation = segment.joint.rotation(jointState).normalize()

            cursorRotation.mul(localRotation)
            cursor.add(CHAIN_AXIS.rotate(cursorRotation).multiply(segment.length))

            nodes += IKChainWorldNode3D(cursor.clone(), Quaternionf(cursorRotation), localRotation)
        }

        return nodes
    }

    companion object {
        val CHAIN_AXIS: Vector get() = FORWARD_VECTOR
    }
}

class IKChainPose3D(
    val position: Vector,
    val orientation: Quaternionf,
    val segments: List<IKJointPose3D>,
) {
    fun clone() = IKChainPose3D(
        position.clone(),
        Quaternionf(orientation),
        segments.map { it.clone() },
    )

    fun copy(other: IKChainPose3D) {
        position.copy(other.position)
        orientation.set(other.orientation)

        for (index in segments.indices) {
            segments[index].copy(other.segments[index])
        }
    }

    fun lerp(other: IKChainPose3D, amount: Double) {
        val t = amount.coerceIn(0.0, 1.0)

        position.copy(position.lerp(other.position, t))
        orientation.slerp(other.orientation, t.toFloat())

        for (index in segments.indices) {
            segments[index].lerpState(other.segments[index], t)
        }
    }
}

private fun directionFromSpherical(azimuth: Double, swing: Double): Vector {
    val sinSwing = sin(swing)
    return Vector(
        cos(azimuth) * sinSwing,
        sin(azimuth) * sinSwing,
        cos(swing),
    ).normalize()
}

internal fun quaternionFromTo(from: Vector, to: Vector, referenceAxis: Vector): Quaternionf {
    val a = from.toVector3f().normalize()
    val b = to.toVector3f().normalize()
    val dot = a.dot(b).coerceIn(-1f, 1f)

    if (dot >= 1f - 1e-6f) return Quaternionf()

    if (dot <= -1f + 1e-6f) {
        val axis = Vector3f(a).cross(referenceAxis.toVector3f())
        if (axis.lengthSquared() < 1e-12f) {
            axis.set(if (kotlin.math.abs(a.x) < 0.9f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f))
            axis.sub(Vector3f(a).mul(a.dot(axis)))
        }
        return Quaternionf().fromAxisAngleRad(axis.normalize(), PI.toFloat())
    }

    val axis = Vector3f(a).cross(b)
    return Quaternionf().fromAxisAngleRad(axis.normalize(), acos(dot))
}

internal fun wrapRadians(angle: Double): Double {
    var wrapped = angle
    while (wrapped > PI) wrapped -= PI * 2
    while (wrapped <= -PI) wrapped += PI * 2
    return wrapped
}

private fun shortestAngleDelta(from: Double, to: Double): Double {
    var delta = to - from
    while (delta > PI) delta -= PI * 2
    while (delta <= -PI) delta += PI * 2
    return delta
}
