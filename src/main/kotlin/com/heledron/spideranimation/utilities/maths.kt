package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import org.bukkit.FluidCollisionMode
import org.bukkit.World
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Transformation
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
