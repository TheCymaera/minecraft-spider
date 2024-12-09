package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
import com.heledron.spideranimation.utilities.averageVector
import com.heledron.spideranimation.utilities.getPitch
import com.heledron.spideranimation.utilities.sendDebugMessage
import com.heledron.spideranimation.utilities.toRadians
import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaterniond
import org.joml.Quaternionf
import java.io.Closeable
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt

interface SpiderComponent : Closeable {
    fun update() {}
    fun render() {}
    override fun close() {}
}

class Spider(
    var location: Location,
    val options: SpiderOptions
): Closeable {
    var gallop = false
    var showDebugVisuals = false

//    var debugOptions = SpiderDebugOptions()

//    var walkGait = Gait.defaultWalk()
//    var gallopGait = Gait.defaultGallop()

    val gait get() = if (gallop) options.gallopGait else options.walkGait

    var isWalking = false
    var isRotatingYaw = false
    var isRotatingPitch = false
    var yawVelocity = 0f

    val velocity = Vector(0.0, 0.0, 0.0)

    val body = SpiderBody(this)

    val cloak = Cloak(this)
    val sound = SoundEffects(this)
    val mount = Mountable(this)
    val pointDetector = PointDetector(this)

    var position: Vector
        get() = location.toVector()
        set(value) {
            location.x = value.x
            location.y = value.y
            location.z = value.z
        }

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
        val diff = newLocation.toVector().subtract(location.toVector())

        location = newLocation
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
        for (component in getComponents()) component.update()
        for (component in getComponents()) component.render()
    }

    fun orientation(): Quaterniond {
        return Quaterniond().rotationYXZ(toRadians(-location.yaw.toDouble()), toRadians(location.pitch.toDouble()), .0)
    }

    fun preferredOrientation(): Quaterniond {
        val yaw = toRadians(-location.yaw.toDouble())
        return Quaterniond().rotationYXZ(yaw, preferredPitch(), .0)
    }

    fun preferredPitch(): Double {
        val frontLeft  = body.legs.getOrNull(0)?.target?.position ?: return .0
        val frontRight = body.legs.getOrNull(1)?.target?.position ?: return .0
        val backLeft  = body.legs.getOrNull(body.legs.size - 2)?.target?.position ?: return .0
        val backRight = body.legs.getOrNull(body.legs.size - 1)?.target?.position ?: return .0

        val leftVector = frontLeft.clone().subtract(backLeft)
        val rightVector = frontRight.clone().subtract(backRight)

        val average = averageVector(listOf(leftVector, rightVector))
        return average.getPitch()
    }
}

