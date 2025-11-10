package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import org.bukkit.util.Vector
import org.joml.*
import kotlin.math.abs
import kotlin.math.sqrt

fun Vector.rotateAroundY(angle: Double, origin: Vector) {
    this.subtract(origin).rotateAroundY(angle).add(origin)
}

fun Quaternionf.getYXZRelative(pivot: Quaternionf): Vector3f {
    val relative = Quaternionf(pivot).difference(this)
    return relative.getEulerAnglesYXZ(Vector3f())
}

fun Vector.getRotationAroundAxis(pivot: Quaternionf): Vector3f {
    val orientation = Quaternionf().rotationTo(FORWARD_VECTOR.toVector3f(), this.toVector3f())
    return orientation.getYXZRelative(pivot)
}

fun Vector.verticalDistance(other: Vector): Double {
    return abs(this.y - other.y)
}

fun Vector.horizontalDistance(other: Vector): Double {
    val x = this.x - other.x
    val z = this.z - other.z
    return sqrt(x * x + z * z)
}

fun Vector.horizontalLength(): Double {
    return sqrt(x * x + z * z)
}

fun List<Vector>.average(): Vector {
    val out = Vector(0, 0, 0)
    for (vector in this) out.add(vector)
    out.multiply(1.0 / this.size)
    return out
}

class SplitDistance(
    val horizontal: Double,
    val vertical: Double
) {
    fun clone(): SplitDistance {
        return SplitDistance(horizontal, vertical)
    }

    fun scale(factor: Double): SplitDistance {
        return SplitDistance(horizontal * factor, vertical * factor)
    }

    fun lerp(target: SplitDistance, factor: Double): SplitDistance {
        return SplitDistance(horizontal.lerp(target.horizontal, factor), vertical.lerp(target.vertical, factor))
    }
}

class SplitDistanceZone(
    val center: Vector,
    val size: SplitDistance
) {
    fun contains(point: Vector): Boolean {
        return center.horizontalDistance(point) <= size.horizontal && center.verticalDistance(point) <= size.vertical
    }

    val horizontal: Double; get() = size.horizontal
    val vertical: Double; get() = size.vertical
}
