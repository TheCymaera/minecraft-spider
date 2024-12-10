package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.body.Leg
import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Quaterniond
import org.joml.Vector3d
import java.io.Closeable

interface SpiderComponent : Closeable {
    fun update() {}
    fun render() {}
    override fun close() {}
}

class Spider(
    val world: World,
    val position: Vector,
    val orientation: Quaterniond,
    val options: SpiderOptions
): Closeable {
    companion object {
        fun fromLocation(location: Location, options: SpiderOptions): Spider {
            val world = location.world!!
            val position = location.toVector()
            val orientation = Quaterniond().rotationYXZ(toRadians(-location.yaw.toDouble()), toRadians(location.pitch.toDouble()), 0.0)
            return Spider(world, position, orientation, options)
        }
    }

    // utils
    fun location(): Location {
        val location = position.toLocation(world)
        location.direction = forwardDirection()
        return location
    }

    fun forwardDirection() = FORWARD_VECTOR.rotate(orientation)

    val gait get() = if (gallop) options.gallopGait else options.walkGait

    // memo
    var preferredPitch = orientation.getEulerAnglesYXZ(Vector3d()).x
    var preferredRoll = orientation.getEulerAnglesYXZ(Vector3d()).z
    var preferredOrientation = Quaterniond(orientation)

    // params
    var gallop = false
    var showDebugVisuals = false

    // state
    var isWalking = false
    var isRotatingYaw = false
    var isRotatingPitch = false
    var yawVelocity = .0

    val velocity = Vector(0.0, 0.0, 0.0)

    // components
    val body = SpiderBody(this)
    val cloak = Cloak(this)
    val sound = SoundEffects(this)
    val mount = Mountable(this)
    val pointDetector = PointDetector(this)

    var behaviour: SpiderComponent = StayStillBehaviour(this)
    set (value) {
        field.close()
        field = value
    }

    var renderer: SpiderComponent = SpiderRenderer(this)
    set (value) {
        field.close()
        field = value
    }

    override fun close() {
        getComponents().forEach { it.close() }
    }

    fun teleport(newLocation: Location) {
        val diff = newLocation.toVector().subtract(position)

        position.copy(newLocation.toVector())
        for (leg in body.legs) leg.endEffector.add(diff)
    }

    fun getComponents() = iterator<SpiderComponent> {
        yield(behaviour)
        yield(cloak)
        yield(body)
        yield(sound)
        yield(mount)
        yield(pointDetector)
        yield(renderer)
    }

    fun update() {
        updatePreferredAngles()
        for (component in getComponents()) component.update()
        for (component in getComponents()) component.render()
    }

    private fun updatePreferredAngles() {
        fun getPos(leg: Leg): Vector {
//            if (leg.isOutsideTriggerZone) return leg.endEffector
            return leg.target.position
        }

        val frontLeft  = getPos(body.legs.getOrNull(0) ?: return)
        val frontRight = getPos(body.legs.getOrNull(1) ?: return)
        val backLeft  = getPos(body.legs.getOrNull(body.legs.size - 2) ?: return)
        val backRight = getPos(body.legs.getOrNull(body.legs.size - 1) ?: return)

        val forwardLeft = frontLeft.clone().subtract(backLeft)
        val forwardRight = frontRight.clone().subtract(backRight)
        val forward = averageVector(listOf(forwardLeft, forwardRight))

        preferredPitch = forward.getPitch().lerp(preferredPitch, .3)

        val sidewaysFront = frontRight.clone().subtract(frontLeft)
        val sidewaysBack = backRight.clone().subtract(backLeft)
        val sideways = averageVector(listOf(sidewaysFront, sidewaysBack))

        preferredRoll = sideways.getPitch().lerp(preferredRoll, .1)

        val currentEuler = orientation.getEulerAnglesYXZ(Vector3d())
        preferredOrientation = Quaterniond().rotationYXZ(currentEuler.y, preferredPitch, preferredRoll)
    }
}

