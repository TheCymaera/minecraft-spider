package com.heledron.spideranimation.utilities.maths

import org.bukkit.Location
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.*
import java.lang.Math
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

val DOWN_VECTOR; get () = Vector(0, -1, 0)
val UP_VECTOR; get () = Vector(0, 1, 0)
val FORWARD_VECTOR; get () = Vector(0, 0, 1)
val BACKWARD_VECTOR; get () = Vector(0, 0, -1)
val LEFT_VECTOR; get () = Vector(-1, 0, 0)
val RIGHT_VECTOR; get () = Vector(1, 0, 0)

fun Vector.toVector4f() = Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), 1f)
fun Vector3f.toVector4f() = Vector4f(x, y, z, 1f)
fun Vector4f.toVector3f() = Vector3f(x, y, z)

fun Vector3f.toVector() = Vector(x.toDouble(), y.toDouble(), z.toDouble())
fun Vector3d.toVector() = Vector(x.toFloat(), y.toFloat(), z.toFloat())
fun Vector4f.toVector() = Vector(x.toDouble(), y.toDouble(), z.toDouble())

fun Vector.copy(vector: Vector3d): Vector {
    this.x = vector.x
    this.y = vector.y
    this.z = vector.z
    return this
}

fun Vector.copy(vector: Vector3f): Vector {
    this.x = vector.x.toDouble()
    this.y = vector.y.toDouble()
    this.z = vector.z.toDouble()
    return this
}

fun Vector.pitch(): Float {
    return -atan2(y, sqrt(x * x + z * z)).toFloat()
}

fun Vector.yaw(): Float {
    return -atan2(-x, z).toFloat()
}

fun Vector.rotate(quaternion: Quaterniond) = copy(Vector3d(x, y, z).rotate(quaternion))

fun Vector.rotate(quaternion: Quaternionf) = copy(Vector3d(x, y, z).rotate(Quaterniond(quaternion)))

fun Vector.lerp(other: Vector, t: Double): Vector {
    this.x = x + (other.x - x) * t
    this.y = y + (other.y - y) * t
    this.z = z + (other.z - z) * t
    return this
}

fun Vector.moveTowards(target: Vector, speed: Double): Vector {
    val diff = target.clone().subtract(this)
    val distance = diff.length()
    if (distance <= speed) {
        this.copy(target)
    } else {
        this.add(diff.multiply(speed / distance))
    }
    return this
}

fun Vector3f.moveTowards(target: Vector3f, speed: Float): Vector3f {
    val diff = Vector3f(target).sub(this)
    val distance = diff.length()
    if (distance <= speed) {
        this.set(target)
    } else {
        this.add(diff.mul(speed / distance))
    }
    return this
}

fun Location.yawRadians(): Float {
    return -yaw.toRadians()
}

fun Location.pitchRadians(): Float {
    return pitch.toRadians()
}

fun Location.getQuaternion(): Quaternionf {
    return Quaternionf().rotateYXZ(yawRadians(), pitchRadians(), 0f)
}

fun Quaterniond.transform(vector: Vector): Vector {
    vector.copy(this.transform(vector.toVector3d()))
    return vector
}

fun Double.lerp(other: Double, t: Double): Double {
    return this * (1 - t) + other * t
}

fun Float.lerp(other: Float, t: Float): Float {
    return this * (1 - t) + other * t
}

fun Int.lerpSafely(other: Int, t: Float): Int {
    if (other == this) return this
    val result = this.toFloat().lerp(other.toFloat(), t).toInt()
    if (result == this && t != 0f) return this.moveTowards(other, 1)
    return result
}

fun Double.moveTowards(target: Double, speed: Double): Double {
    val distance = target - this
    return if (abs(distance) < speed) target else this + speed * distance.sign
}

fun Float.moveTowards(target: Float, speed: Float): Float {
    val distance = target - this
    return if (abs(distance) < speed) target else this + speed * distance.sign
}

fun Int.moveTowards(target: Int, speed: Int): Int {
    val distance = target - this
    return if (abs(distance) < speed) target else this + speed * distance.sign
}

fun Double.toRadians(): Double {
    return Math.toRadians(this)
}

fun Float.toRadians(): Float {
    return Math.toRadians(this.toDouble()).toFloat()
}

fun Double.toDegrees(): Double {
    return Math.toDegrees(this)
}

fun Float.toDegrees(): Float {
    return Math.toDegrees(this.toDouble()).toFloat()
}


fun Double.normalize(min: Double, max: Double): Double {
    return (this - min) / (max - min)
}

fun Double.denormalize(min: Double, max: Double): Double {
    return this * (max - min) + min
}

fun Float.normalize(min: Float, max: Float): Float {
    return (this - min) / (max - min)
}

fun Float.denormalize(min: Float, max: Float): Float {
    return this * (max - min) + min
}

fun Transformation.normal(): Vector3f {
    val rotation = Quaternionf(leftRotation).mul(rightRotation)
    val forward = Vector3f(0f, 0f, 1f).rotate(rotation)
    return forward.normalize()
}

fun shearMatrix(
    xy: Float,
    xz: Float,
    yx: Float,
    yz: Float,
    zx: Float,
    zy: Float,
): Matrix4f {
    return Matrix4f(
        1f, xy, xz, 0f,
        yx, 1f, yz, 0f,
        zx, zy, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}

fun Matrix4f.shear(
    xy: Float = 0f,
    xz: Float = 0f,
    yx: Float = 0f,
    yz: Float = 0f,
    zx: Float = 0f,
    zy: Float = 0f,
): Matrix4f = this.mul(shearMatrix(
    xy = xy,
    xz = xz,
    yx = yx,
    yz = yz,
    zx = zx,
    zy = zy,
))

fun Float.eased(): Float {
    return this * this * (3 - 2 * this)
}


fun List<Vector>.average(): Vector {
    val out = Vector(0.0, 0.0, 0.0)
    for (vector in this) out.add(vector)
    return out.multiply(1.0 / this.size)
}

fun List<Vector3f>.average(): Vector3f {
    val out = Vector3f()
    for (vector in this) out.add(vector)
    return out.mul(1f / this.size)
}