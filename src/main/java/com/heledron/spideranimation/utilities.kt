package com.heledron.spideranimation

import net.md_5.bungee.api.ChatMessageType
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.*
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt


fun runLater(delay: Long, task: () -> Unit): Closeable {
    val plugin = SpiderAnimationPlugin.instance
    val handler = plugin.server.scheduler.runTaskLater(plugin, task, delay)
    return Closeable {
        handler.cancel()
    }
}

fun interval(delay: Long, period: Long, task: () -> Unit): Closeable {
    val plugin = SpiderAnimationPlugin.instance
    val handler = plugin.server.scheduler.runTaskTimer(plugin, task, delay, period)
    return Closeable {
        handler.cancel()
    }
}

fun addEventListener(listener: Listener): Closeable {
    val plugin = SpiderAnimationPlugin.instance
    plugin.server.pluginManager.registerEvents(listener, plugin)
    return Closeable {
        org.bukkit.event.HandlerList.unregisterAll(listener)
    }
}


class SeriesScheduler {
    var time = 0L

    fun sleep(time: Long) {
        this.time += time
    }

    fun run(task: () -> Unit) {
        runLater(time, task)
    }
}

class EventEmitter {
    val listeners = mutableListOf<() -> Unit>()
    fun listen(listener: () -> Unit): Closeable {
        listeners.add(listener)
        return Closeable { listeners.remove(listener) }
    }

    fun emit() {
        for (listener in listeners) listener()
    }
}

fun firstPlayer(): org.bukkit.entity.Player? {
    return Bukkit.getOnlinePlayers().firstOrNull()
}

fun sendDebugMessage(message: String) {
    sendActionBar(firstPlayer() ?: return, message)
}

fun sendActionBar(player: org.bukkit.entity.Player, message: String) {
//    player.sendActionBar(message)
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent(message))
}

fun raycastGround(location: Location, direction: Vector, maxDistance: Double): RayTraceResult? {
    return location.world!!.rayTraceBlocks(location, direction, maxDistance, FluidCollisionMode.NEVER, true)
}

fun isOnGround(location: Location): Boolean {
    return raycastGround(location, DOWN_VECTOR, 0.001) != null
}

val DOWN_VECTOR; get () = Vector(0, -1, 0)
val UP_VECTOR; get () = Vector(0, 1, 0)

data class CollisionResult(val position: Vector, val offset: Vector)

fun resolveCollision(location: Location, direction: Vector): CollisionResult? {
    val ray = location.world!!.rayTraceBlocks(location.clone().subtract(direction), direction, direction.length(), FluidCollisionMode.NEVER, true)
    if (ray != null) {
        val newLocation = ray.hitPosition.toLocation(location.world!!)
        return CollisionResult(newLocation.toVector(), ray.hitPosition.subtract(location.toVector()))
    }

    return null
}

fun lerpNumberByFactor(current: Double, target: Double, factor: Double): Double {
    return current + (target - current) * factor
}

fun lerpNumberByConstant(current: Double, target: Double, constant: Double): Double {
    val distance = target - current
    return if (abs(distance) < constant) target else current + constant * distance.sign
}

fun lerpVectorByConstant(current: Vector, target: Vector, constant: Double) {
    val diff = target.clone().subtract(current)
    val distance = diff.length()
    if (distance <= constant) {
        current.copy(target)
    } else {
        current.add(diff.multiply(constant / distance))
    }
}

fun lerpVectorByFactor(current: Vector, target: Vector, factor: Double) {
    current.add(target.clone().subtract(current).multiply(factor))
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

fun rotateYAbout(out: Vector, angle: Double, origin: Vector) {
    out.subtract(origin).rotateAroundY(angle).add(origin)
}

class SplitDistance(val horizontal: Double, val vertical: Double) {
    fun contains(origin: Vector, point: Vector): Boolean {
        return horizontalDistance(origin, point) <= horizontal && verticalDistance(origin, point) <= vertical

    }
    companion object {
        fun distance(a: Vector, b: Vector): SplitDistance {
            return SplitDistance(horizontalDistance(a, b), verticalDistance(a, b))
        }
    }
}

fun averageVector(vectors: List<Vector>): Vector {
    val out = Vector(0, 0, 0)
    for (vector in vectors) out.add(vector)
    out.multiply(1.0 / vectors.size)
    return out
}

fun copyLocation(location: Location, from: Location) {
    location.world = from.world
    location.x = from.x
    location.y = from.y
    location.z = from.z
    location.pitch = from.pitch
    location.yaw = from.yaw
}

fun playSound(location: Location, sound: org.bukkit.Sound, volume: Float, pitch: Float) {
    location.world!!.playSound(location, sound, volume, pitch)
}

fun <T : Entity> spawnEntity(location: Location, clazz: Class<T>, initializer: (T) -> Unit): T {
    return location.world!!.spawn(location, clazz, initializer)
}

fun spawnParticle(particle: org.bukkit.Particle, location: Location, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double) {
    location.world!!.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)
}

fun <T> spawnParticle(particle: org.bukkit.Particle, location: Location, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double, data: T) {
    location.world!!.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data)
}

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

fun transformFromMatrix(matrix: Matrix4f): Transformation {
    val translation = matrix.getTranslation(Vector3f())
    val rotation = matrix.getUnnormalizedRotation(Quaternionf())
    val scale = matrix.getScale(Vector3f())

    return Transformation(translation, rotation, scale, Quaternionf())
}

fun applyTransformationWithInterpolation(entity: BlockDisplay, transformation: Transformation) {
    if (entity.transformation != transformation) {
        entity.transformation = transformation
        entity.interpolationDelay = 0
    }
}

fun applyTransformationWithInterpolation(entity: BlockDisplay, matrix: Matrix4f) {
    applyTransformationWithInterpolation(entity, transformFromMatrix(matrix))
}