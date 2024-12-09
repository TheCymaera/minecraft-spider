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

//fun Vector3f.toVector(): Vector {
//    return Vector(x.toDouble(), y.toDouble(), z.toDouble())
//}
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

//fun Vector.rotateAround(rotation: Quaternionf, origin: Vector) {
//    this.subtract(origin).rotate(rotation).add(origin)
//}

//fun Quaternionf.transform(vector: Vector): Vector {
//    vector.copy(this.transform(vector.toVector3d()))
//    return vector
//}

fun Quaterniond.rotationToYX(fromDir: Vector3d, toDir: Vector3d): Quaterniond {
    this.rotationTo(fromDir, toDir)
    val euler = this.getEulerAnglesYXZ(Vector3d())
    return Quaterniond().rotationYXZ(euler.y, euler.x, .0)
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

fun verticalDistance(a: Vector, b: Vector): Double {
    return abs(a.y - b.y)
}

fun horizontalDistance(a: Vector, b: Vector): Double {
    val x = a.x - b.x
    val z = a.z - b.z
    return sqrt(x * x + z * z)
}

fun horizontalLength(vector: Vector): Double {
    return sqrt(vector.x * vector.x + vector.z * vector.z)
}

fun averageVector(vectors: List<Vector>): Vector {
    val out = Vector(0, 0, 0)
    for (vector in vectors) out.add(vector)
    out.multiply(1.0 / vectors.size)
    return out
}

class SplitDistance(
    val horizontal: Double,
    val vertical: Double
) {
    fun contains(origin: Vector, point: Vector): Boolean {
        return horizontalDistance(origin, point) <= horizontal && verticalDistance(origin, point) <= vertical
    }

    fun scale(factor: Double): SplitDistance {
        return SplitDistance(horizontal * factor, vertical * factor)
    }

    fun lerp(target: SplitDistance, factor: Double): SplitDistance {
        return SplitDistance(horizontal.lerp(target.horizontal, factor), vertical.lerp(target.vertical, factor))
    }
}

val DOWN_VECTOR; get () = Vector(0, -1, 0)
val UP_VECTOR; get () = Vector(0, 1, 0)



fun pointInPolygon(point: Vector2d, polygon: List<Vector2d>): Boolean {
    // count intersections
    var count = 0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]

        if (a.y <= point.y && b.y > point.y || b.y <= point.y && a.y > point.y) {
            val slope = (b.x - a.x) / (b.y - a.y)
            val intersect = a.x + (point.y - a.y) * slope
            if (intersect < point.x) count++
        }
    }

    return count % 2 == 1
}

fun nearestPointInPolygon(point: Vector2d, polygon: List<Vector2d>): Vector2d {
    var closest = polygon[0]
    var closestDistance = point.distance(closest)

    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]

        val closestOnLine = nearestPointOnClampedLine(point, a, b) ?: continue
        val distance = point.distance(closestOnLine)

        if (distance < closestDistance) {
            closest = closestOnLine
            closestDistance = distance
        }
    }

    return closest
}

fun nearestPointOnClampedLine(point: Vector2d, a: Vector2d, b: Vector2d): Vector2d {
    val ap = Vector2d(point.x - a.x, point.y - a.y)
    val ab = Vector2d(b.x - a.x, b.y - a.y)

    val dotProduct = ap.dot(ab)
    val lengthAB = a.distance(b)

    val t = dotProduct / (lengthAB * lengthAB)

    // Ensure the nearest point lies within the line segment
    val tClamped = t.coerceIn(0.0, 1.0)

    val nearestX = a.x + tClamped * ab.x
    val nearestY = a.y + tClamped * ab.y

    return Vector2d(nearestX, nearestY)
}