package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.body.Leg
import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.misc.*
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.Closeable

interface SpiderComponent : Closeable {
    fun update() {}
    fun render() {}
    override fun close() {}
}

class Spider(
    val world: World,
    val position: Vector,
    val orientation: Quaternionf,
    val options: SpiderOptions
): Closeable {
    companion object {
        fun fromLocation(location: Location, options: SpiderOptions): Spider {
            val world = location.world!!
            val position = location.toVector()
            val orientation = Quaternionf().rotationYXZ(toRadians(-location.yaw), toRadians(location.pitch), 0f)
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

    val moveGait get() = if (gallop) options.gallopGait else options.walkGait

    // memo
    var gait = options.stationaryGait.clone()
    var preferredPitch = orientation.getEulerAnglesYXZ(Vector3f()).x
    var preferredRoll = orientation.getEulerAnglesYXZ(Vector3f()).z
    var preferredOrientation = Quaternionf(orientation)

    // params
    var gallop = false
    var showDebugVisuals = false

    // state
    var isWalking = false
    var isRotatingYaw = false
//    var isRotatingPitch = false

    val velocity = Vector(0.0, 0.0, 0.0)
    val rotationalVelocity = Vector3f(0f,0f,0f)

    // components
    val body = SpiderBody(this)
    val tridentDetector = TridentHitDetector(this)
    val cloak = Cloak(this)
    val sound = SoundsAndParticles(this)
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
        yield(tridentDetector)
        yield(sound)
        yield(mount)
        yield(pointDetector)
        yield(renderer)
    }

    fun update() {
        updatePreferredAngles()
        for (component in getComponents()) {
            updateGait()
            component.update()
        }
        for (component in getComponents()) component.render()
    }

    private fun updateGait() {
        if (isRotatingYaw) {
            gait = moveGait.gait.clone()
            return
        }

        if (!isWalking && !velocity.isZero) {
            gait = options.movingButNotWalkingGait.clone()
            return
        }

        val speedFraction = velocity.length() / moveGait.maxSpeed
        gait = options.stationaryGait.clone().lerp(moveGait.gait, speedFraction)
    }

    private fun updatePreferredAngles() {
        val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

        if (moveGait.disableAdvancedRotation) {
            preferredPitch = .0f
            preferredRoll = .0f
            preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, .0f, .0f)
            return
        }

        fun getPos(leg: Leg): Vector {
//            if (leg.isOutsideTriggerZone) return leg.endEffector
            return leg.groundPosition ?: leg.restPosition
        }

        val frontLeft  = getPos(body.legs.getOrNull(0) ?: return)
        val frontRight = getPos(body.legs.getOrNull(1) ?: return)
        val backLeft  = getPos(body.legs.getOrNull(body.legs.size - 2) ?: return)
        val backRight = getPos(body.legs.getOrNull(body.legs.size - 1) ?: return)

        val forwardLeft = frontLeft.clone().subtract(backLeft)
        val forwardRight = frontRight.clone().subtract(backRight)
        val forward = listOf(forwardLeft, forwardRight).average()

        preferredPitch = forward.getPitch()//.lerp(currentEuler.x, .3f)

        val sideways = Vector(0.0,0.0,0.0)
        for (i in 0 until body.legs.size step 2) {
            val left = body.legs.getOrNull(i) ?: continue
            val right = body.legs.getOrNull(i + 1) ?: continue

            sideways.add(getPos(right).clone().subtract(getPos(left)))
        }

        preferredRoll = sideways.getPitch()//.lerp(currentEuler.z, .3f)

        preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, preferredPitch, preferredRoll)
    }
}

