package com.heledron.spideranimation.utilities

import org.bukkit.util.Vector
import org.joml.*
import java.lang.Math
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

fun Double.lerp(target: Double, factor: Double): Double {
    return this + (target - this) * factor
}

fun Float.lerp(target: Float, factor: Float): Float {
    return this + (target - this) * factor
}

fun Double.moveTowards(target: Double, speed: Double): Double {
    val distance = target - this
    return if (abs(distance) < speed) target else this + speed * distance.sign
}

fun Float.moveTowards(target: Float, speed: Float): Float {
    val distance = target - this
    return if (abs(distance) < speed) target else this + speed * distance.sign
}

fun Vector.moveTowards(target: Vector, constant: Double) {
    val diff = target.clone().subtract(this)
    val distance = diff.length()
    if (distance <= constant) {
        this.copy(target)
    } else {
        this.add(diff.multiply(constant / distance))
    }
}

fun Vector.lerp(target: Vector, factor: Double) {
    this.add(target.clone().subtract(this).multiply(factor))
}

fun Vector.copy(vec: Vector3d) {
    this.x = vec.x
    this.y = vec.y
    this.z = vec.z
}

fun Vector.copy(vec3: Vector3f) {
    this.x = vec3.x.toDouble()
    this.y = vec3.y.toDouble()
    this.z = vec3.z.toDouble()
}

fun Vector.rotateAroundY(angle: Double, origin: Vector) {
    this.subtract(origin).rotateAroundY(angle).add(origin)
}

fun Vector.rotate(rotation: Quaterniond): Vector {
    this.copy(rotation.transform(this.toVector3d()))
    return this
}

fun Vector.getPitch(): Double {
    val x = this.x
    val y = this.y
    val z = this.z
    val xz = sqrt(x * x + z * z)
    val pitch = atan2(-y, xz)
    return pitch
}

fun Vector.getYaw(): Double {
    val x = this.x
    val z = this.z
    val yaw = atan2(-x, z)
    return yaw
}

//fun Quaterniond.rotationToYX(fromDir: Vector3d, toDir: Vector3d): Quaterniond {
//    this.rotationTo(fromDir, toDir)
//    val euler = this.getEulerAnglesYXZ(Vector3d())
//    return this.rotationYXZ(euler.y, euler.x, .0)
//}

fun Quaterniond.stripRelativeZ(pivot: Quaterniond) {
    val relative = Quaterniond(pivot).difference(this)

    // remove z rotation
    val euler = relative.getEulerAnglesYXZ(Vector3d())
    relative.rotationYXZ(euler.y, euler.x, .0)

    this.set(pivot).mul(relative)
}

fun Quaternionf.stripRelativeZ(pivot: Quaternionf): Quaternionf {
    val relative = Quaternionf(pivot).difference(this)

    // remove z rotation
    val euler = relative.getEulerAnglesYXZ(Vector3f())
    relative.rotationYXZ(euler.y, euler.x, .0f)

    return this.set(pivot).mul(relative)
}

fun toDegrees(radians: Double): Double {
    return Math.toDegrees(radians)
}

fun toDegrees(radians: Float): Float {
    return Math.toDegrees(radians.toDouble()).toFloat()
}

fun toRadians(degrees: Double): Double {
    return Math.toRadians(degrees)
}

fun toRadians(degrees: Float): Float {
    return Math.toRadians(degrees.toDouble()).toFloat()
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
//        return point.distance(center) <= radius.horizontal
        return center.horizontalDistance(point) <= size.horizontal && center.verticalDistance(point) <= size.vertical
    }

    val horizontal: Double; get() = size.horizontal
    val vertical: Double; get() = size.vertical
}


val DOWN_VECTOR; get () = Vector(0, -1, 0)
val UP_VECTOR; get () = Vector(0, 1, 0)
val FORWARD_VECTOR; get () = Vector(0, 0, 1)
val LEFT_VECTOR; get () = Vector(-1, 0, 0)
val RIGHT_VECTOR; get () = Vector(1, 0, 0)


