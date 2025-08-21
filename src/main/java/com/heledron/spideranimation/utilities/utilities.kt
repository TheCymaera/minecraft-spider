package com.heledron.spideranimation.utilities

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.CommandMinecart
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Closeable

lateinit var currentPlugin: JavaPlugin

fun runLater(delay: Long, task: () -> Unit): Closeable {
    val plugin = currentPlugin
    val handler = plugin.server.scheduler.runTaskLater(plugin, task, delay)
    return Closeable {
        handler.cancel()
    }
}

fun interval(delay: Long, period: Long, task: (it: Closeable) -> Unit): Closeable {
    val plugin = currentPlugin
    lateinit var handler: org.bukkit.scheduler.BukkitTask
    val closeable = Closeable { handler.cancel() }
    handler = plugin.server.scheduler.runTaskTimer(plugin, Runnable { task(closeable) }, delay, period)
    return closeable
}

fun onTick(task: (it: Closeable) -> Unit) = interval(0, 1, task)

fun addEventListener(listener: Listener): Closeable {
    val plugin = currentPlugin
    plugin.server.pluginManager.registerEvents(listener, plugin)
    return Closeable {
        org.bukkit.event.HandlerList.unregisterAll(listener)
    }
}

fun onInteractEntity(listener: (Player, Entity, EquipmentSlot) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @org.bukkit.event.EventHandler
        fun onInteract(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
            listener(event.player, event.rightClicked, event.hand)
        }
    })
}

fun onSpawnEntity(listener: (Entity, World) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @org.bukkit.event.EventHandler
        fun onSpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
            listener(event.entity, event.entity.world)
        }
    })
}


private var commandBlockMinecart: CommandMinecart? = null
fun runCommandSilently(command: String, location: Location = Bukkit.getWorlds().first().spawnLocation) {
    val server = Bukkit.getServer()

    val commandBlockMinecart = commandBlockMinecart ?: spawnEntity(location, CommandMinecart::class.java) {
        commandBlockMinecart = it
        it.remove()
    }

    server.dispatchCommand(commandBlockMinecart, command)
}

fun onGestureUseItem(listener: (Player, ItemStack) -> Unit): Closeable {
    return addEventListener(object : Listener {
        @org.bukkit.event.EventHandler
        fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
            if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
            if (event.useInteractedBlock() == Event.Result.ALLOW && !event.player.isSneaking) return
            listener(event.player, event.item ?: return)
        }
    })
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
    private val listeners = mutableListOf<() -> Unit>()
    fun listen(listener: () -> Unit): Closeable {
        listeners.add(listener)
        return Closeable { listeners.remove(listener) }
    }

    fun emit() {
        for (listener in listeners) listener()
    }
}

fun firstPlayer(): Player? {
    return Bukkit.getOnlinePlayers().firstOrNull()
}

fun sendDebugMessage(message: String) {
    sendActionBar(firstPlayer() ?: return, message)
}

fun sendActionBar(player: Player, message: String) {
    player.sendActionBar(Component.text(message))
}

fun raycastGround(location: Location, direction: Vector, maxDistance: Double): RayTraceResult? {
    return location.world!!.rayTraceBlocks(location, direction, maxDistance, FluidCollisionMode.NEVER, true)
}

fun World.raycastGround(position: Vector, direction: Vector, maxDistance: Double): RayTraceResult? {
    return raycastGround(position.toLocation(this), direction, maxDistance)
}

fun World.isOnGround(position: Vector, downVector: Vector = DOWN_VECTOR): Boolean {
    return raycastGround(position.toLocation(this), downVector, 0.001) != null
}

data class CollisionResult(val position: Vector, val offset: Vector)

fun resolveCollision(location: Location, direction: Vector): CollisionResult? {
    val ray = location.world!!.rayTraceBlocks(location.clone().subtract(direction), direction, direction.length(), FluidCollisionMode.NEVER, true)
    if (ray != null) {
        val newLocation = ray.hitPosition.toLocation(location.world!!)
        return CollisionResult(newLocation.toVector(), ray.hitPosition.subtract(location.toVector()))
    }

    return null
}

fun World.resolveCollision(position: Vector, direction: Vector): CollisionResult? {
    return resolveCollision(position.toLocation(this), direction)
}

fun playSound(location: Location, sound: org.bukkit.Sound, volume: Float, pitch: Float) {
    location.world!!.playSound(location, sound, volume, pitch)
}

fun World.playSound(position: Vector, sound: org.bukkit.Sound, volume: Float, pitch: Float) {
    playSound(position.toLocation(this), sound, volume, pitch)
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

fun centeredMatrix(xSize: Float, ySize: Float, zSize: Float): Matrix4f {
    return Matrix4f()
        .scale(xSize, ySize, zSize)
        .translate(-.5f, -.5f, -.5f)
}

fun matrixFromTransform(transformation: Transformation): Matrix4f {
    val matrix = Matrix4f()
    matrix.translate(transformation.translation)
    matrix.rotate(transformation.leftRotation)
    matrix.scale(transformation.scale)
    matrix.rotate(transformation.rightRotation)
    return matrix
}



fun Display.applyTransformationWithInterpolation(transformation: Transformation) {
    if (this.transformation == transformation) return
    this.transformation = transformation
    this.interpolationDelay = 0
}

fun Display.applyTransformationWithInterpolation(matrix: Matrix4f) {
    val oldTransform = this.transformation
    setTransformationMatrix(matrix)

    if (oldTransform == this.transformation) return
    this.interpolationDelay = 0
}