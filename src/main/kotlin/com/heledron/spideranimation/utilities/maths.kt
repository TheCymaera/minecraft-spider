package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import org.bukkit.FluidCollisionMode
import org.bukkit.World
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.*
import javax.sound.sampled.Line
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

class Capsule(
    val point1: Vector,
    val point2: Vector,
    val radius: Double,
) {
    fun line() = LineSegment(point1, point2)
    fun contains(point: Vector): Boolean = line().distanceSquared(point) <= radius * radius
    fun axis() = point2.clone().subtract(point1)
}

class LineSegment(
    val point1: Vector,
    val point2: Vector,
) {
    companion object {
        fun fromOffset(origin: Vector, offset: Vector) = LineSegment(origin, origin.clone().add(offset))
    }

    fun vector() = point2.clone().subtract(point1)

    fun distance(point: Vector): Double = sqrt(distanceSquared(point))

    fun distanceSquared(point: Vector): Double {
        val abX = point2.x - point1.x
        val abY = point2.y - point1.y
        val abZ = point2.z - point1.z
        val lenSq = abX * abX + abY * abY + abZ * abZ
        val apX = point.x - point1.x
        val apY = point.y - point1.y
        val apZ = point.z - point1.z
        val t = if (lenSq < 1e-12) 0.0 else ((apX * abX + apY * abY + apZ * abZ) / lenSq).coerceIn(0.0, 1.0)
        val dx = point.x - (point1.x + abX * t)
        val dy = point.y - (point1.y + abY * t)
        val dz = point.z - (point1.z + abZ * t)
        return dx * dx + dy * dy + dz * dz
    }
}

fun World.raycastGround(position: Vector, direction: Vector, maxDistance: Double): RayTraceResult? {
    val location = position.toLocation(this)
    return this.rayTraceBlocks(location, direction, maxDistance, FluidCollisionMode.NEVER, true)
}

fun World.isOnGround(position: Vector, downVector: Vector = DOWN_VECTOR): Boolean {
    return this.raycastGround(position, downVector, 0.001) != null
}

data class CollisionResult(val position: Vector, val offset: Vector)

fun World.resolveCollision(position: Vector, direction: Vector): CollisionResult? {
    val location = position.toLocation(this)
    val ray = this.rayTraceBlocks(location.subtract(direction), direction, direction.length(), FluidCollisionMode.NEVER, true)
    if (ray != null) {
        return CollisionResult(ray.hitPosition, ray.hitPosition.clone().subtract(position))
    }

    return null
}

fun lookingAtPoint(eye: Vector, direction: Vector, point: Vector, tolerance: Double): Boolean {
    val pointDistance = eye.distance(point)
    val lookingAtPoint = eye.clone().add(direction.clone().multiply(pointDistance))
    return lookingAtPoint.distance(point) < tolerance
}

fun centredTransform(xSize: Float, ySize: Float, zSize: Float): Transformation {
    return Transformation(
        Vector3f(-xSize / 2, -ySize / 2, -zSize / 2),
        AxisAngle4f(0f, 0f, 0f, 1f),
        Vector3f(xSize, ySize, zSize),
        AxisAngle4f(0f, 0f, 0f, 1f)
    )
}

fun matrixFromTransform(transformation: Transformation): Matrix4f {
    val matrix = Matrix4f()
    matrix.translate(transformation.translation)
    matrix.rotate(transformation.leftRotation)
    matrix.scale(transformation.scale)
    matrix.rotate(transformation.rightRotation)
    return matrix
}
