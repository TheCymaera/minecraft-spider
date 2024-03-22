package com.heledron.spideranimation

import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.*
import java.lang.Math
import java.util.*

class RenderDebugOptions(
    var legTarget: Boolean = false,
    var legTriggerZone: Boolean = false,
    var legRestPosition: Boolean = false,
    var legEndEffector: Boolean = false,
    var spiderDirection: Boolean = false,
) {
    companion object {
        fun all(): RenderDebugOptions {
            return RenderDebugOptions(
                legTarget = true,
                legTriggerZone = true,
                legRestPosition = true,
                legEndEffector = true,
                spiderDirection = true,
            )
        }

        fun none(): RenderDebugOptions {
            return RenderDebugOptions(
                legTarget = false,
                legTriggerZone = false,
                legRestPosition = false,
                legEndEffector = false,
                spiderDirection = false,
            )
        }
    }
}

object BlockDisplayRenderer : Renderer {
    val displays = HashMap<Any, BlockDisplay>()

    enum class Identifier {
        TARGET,
        LEG_TARGET_POSITION,
        LEG_TRIGGER_ZONE,
        LEG_REST_POSITION,
        LEG_REST_POSITION_CENTER,
        LEG_END_EFFECTOR,
        DIRECTION;

        companion object {
            fun chainSegment(segment: ChainSegment): Any { return segment }
            fun legTargetPosition(leg: Leg): Any { return Pair(LEG_TARGET_POSITION, leg) }
            fun legTriggerZone(leg: Leg): Any { return Pair(LEG_TRIGGER_ZONE, leg) }
            fun legRestPosition(leg: Leg): Any { return Pair(LEG_REST_POSITION, leg) }
            fun legRestPositionCenter(leg: Leg): Any { return Pair(LEG_REST_POSITION_CENTER, leg) }
            fun legEndEffector(leg: Leg): Any { return Pair(LEG_END_EFFECTOR, leg) }
            fun direction(spider: Spider): Any { return Pair(DIRECTION, spider) }
            fun target(id: Any): Any { return Pair(TARGET, id) }
        }
    }

    fun render(identifier: Any, location: Location, init: (BlockDisplay) -> Unit): BlockDisplay {
        if (displays.size > 100) {
            throw IllegalStateException("Too many displays. Check for memory leaks.")
        }


        val entity = displays.getOrPut(identifier) {
            location.world.spawn(location, BlockDisplay::class.java) {
                init(it)
            }
        }

        entity.teleport(location)

        return entity
    }

    fun renderIf(identifier: Any, location: Location, condition: Boolean, init: (BlockDisplay) -> Unit): BlockDisplay? {
        if (!condition) {
            clear(identifier)
            return null
        }

        return render(identifier, location, init)
    }

    override fun clear(identifier: Any) {
        displays[identifier]?.remove()
        displays.remove(identifier)
    }

    fun clearAll() {
        for (entity in displays.values) entity.remove()
        displays.clear()
    }

    override fun renderSpider(spider: Spider, debug: RenderDebugOptions) {
        for (leg in spider.legs) {
            renderLeg(leg, debug)
        }

        renderDirection(spider, debug.spiderDirection)

        if (spider.didHitGround) {
            spider.location.world.playSound(spider.location, Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.0f, .8f)
        }
    }

    override fun clearSpider(spider: Spider) {
        for (leg in spider.legs) {
            for (segment in leg.chain.segments) {
                clear(Identifier.chainSegment(segment))
            }

            clear(Identifier.legTargetPosition(leg))
            clear(Identifier.legTriggerZone(leg))
            clear(Identifier.legRestPosition(leg))
            clear(Identifier.legRestPositionCenter(leg))
            clear(Identifier.legEndEffector(leg))
        }

        clear(Identifier.direction(spider))
    }

    fun renderDirection(spider: Spider, render: Boolean) {
        val location = spider.location.clone()
        location.add(spider.location.direction)

        renderIf(Identifier.direction(spider), location, render) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f
            it.transformation = centredTransform(size, size, size)
        }?.apply {
            this.block = Material.EMERALD_BLOCK.createBlockData()
            this.teleport(location)
        }
    }

    override fun renderTarget(location: Location, identifier: Any) {
        render(identifier, location) {
            it.block = Material.REDSTONE_BLOCK.createBlockData()
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = .25f
            val ySize = .25f
            it.transformation = centredTransform(size, ySize, size)
        }
    }

    fun renderLeg(leg: Leg, debug: RenderDebugOptions) {
        var root = leg.chain.root.toLocation(leg.parent.location.world)

        val upVector = run {
            // up vector is the cross product of the y-axis and the end-effector direction
            val direction = leg.chain.segments.last().position.clone().subtract(root.toVector())
            direction.clone().crossProduct(Vector(0, 1, 0))
        }

        val maxThickness = 1.5f/16f * 4
        val minThickness = 1.5f/16f * 1

        for ((i, segment) in leg.chain.segments.withIndex()) {
            val thickness = (leg.chain.segments.size - i - 1) * (maxThickness - minThickness) / leg.chain.segments.size + minThickness
            renderSegment(root, segment, thickness, upVector)
            root = segment.position.toLocation(leg.parent.location.world)
        }

        renderLegTargetPosition(leg, debug.legTarget)
        renderLegTriggerZone(leg, debug.legTriggerZone)
        renderLegRestPosition(leg, debug.legRestPosition)
        renderLegEndEffector(leg, debug.legEndEffector)

        if (leg.didStep) {
            val location = leg.endEffector.toLocation(leg.parent.location.world)
            val volume = .3f
            val pitch = 1.0f + Math.random().toFloat() * 0.1f
            location.world.playSound(location, Sound.BLOCK_NETHERITE_BLOCK_STEP, volume, pitch)
        }
    }

    fun renderLegTargetPosition(leg: Leg, renderDebug: Boolean) {
        val location = leg.targetPosition.toLocation(leg.parent.location.world)

        renderIf(Identifier.legTargetPosition(leg), location, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            it.block = Material.GREEN_STAINED_GLASS.createBlockData()

            val size = 0.2f
            it.transformation = centredTransform(size, size, size)
        }
    }

    fun renderLegRestPosition(leg: Leg, renderDebug: Boolean) {
        val location = leg.restPosition.toLocation(leg.parent.location.world)

        val yMax = location.y + leg.scanGroundAbove
        val yMin = location.y - leg.scanGroundBelow
        val yCenter = (yMax + yMin) / 2
        val ySize = yMax - yMin

        location.y = yCenter

        renderIf(Identifier.legRestPosition(leg), location, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.05f
            it.transformation = centredTransform(size, ySize.toFloat(), size)
        }?.apply {
            this.block = if (leg.isStranded) Material.COPPER_BLOCK.createBlockData() else Material.GOLD_BLOCK.createBlockData()
            this.teleport(location)
        }

        renderIf(Identifier.legRestPositionCenter(leg), location, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f
            val ySize = 0.05f
            it.transformation = centredTransform(size, ySize, size)
        }?.apply {
            this.block = if (leg.isStranded) Material.COPPER_BLOCK.createBlockData() else Material.GOLD_BLOCK.createBlockData()
            this.teleport(location)
        }
    }

    fun renderLegTriggerZone(leg: Leg, renderDebug: Boolean) {
        val location = leg.targetPosition.toLocation(leg.parent.location.world)

        renderIf(Identifier.legTriggerZone(leg), location, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)
            it.interpolationDuration = 1
        }?.apply {
            val size = 2 * leg.triggerDistance().toFloat()
            val ySize = 0.02f
            val transform = centredTransform(size, ySize, size)

            this.block = if (leg.uncomfortable) Material.RED_STAINED_GLASS.createBlockData() else Material.CYAN_STAINED_GLASS.createBlockData()

            if (this.transformation != transform) {
                this.transformation = transform
                this.interpolationDelay = 0
            }
        }
    }

    fun renderLegEndEffector(leg: Leg, renderDebug: Boolean) {
        val position = leg.endEffector.toLocation(leg.parent.location.world)

        renderIf(Identifier.legEndEffector(leg), position, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.15f
            it.transformation = centredTransform(size, size, size)
        }?.apply {
            this.block = if (leg.onGround) Material.DIAMOND_BLOCK.createBlockData() else Material.REDSTONE_BLOCK.createBlockData()
        }
    }

    fun renderSegment(location: Location, segment: ChainSegment, thickness: Float, upVector: Vector): BlockDisplay {
        val xSize = thickness
        val ySize = thickness
        val zSize = segment.length.toFloat()
        return render(Identifier.chainSegment(segment), location) {
            it.block = Material.NETHERITE_BLOCK.createBlockData()
            it.teleportDuration = 1
            it.interpolationDuration = 1
        }.apply {
            val vector = segment.position.clone().subtract(location.toVector()).toVector3f()

            val matrix = Matrix4f()
            matrix.rotateTowards(vector, upVector.toVector3f())
            matrix.translate(-xSize / 2, -ySize / 2, 0f)
            matrix.scale(xSize, ySize, zSize)

            // this.setTransformationMatrix(matrix)

            val transform = transformFromMatrix(matrix)
            if (this.transformation != transform) {
                this.transformation = transform
                this.interpolationDelay = 0
            }
        }
    }
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

object ParticleRenderer : Renderer {
    fun renderLine(location: Location, line: Vector, thickness: Double) {
        val gap = .05

        val amount = location.toVector().distance(line) / gap
        val step = line.clone().subtract(location.toVector()).multiply(1 / amount)

        val current = location.clone()

        for (i in 0..amount.toInt()) {
            location.world.spawnParticle(Particle.WATER_BUBBLE, current, 1, thickness, thickness, thickness, 0.0)
            current.add(step)
        }
    }

    fun renderLeg(leg: Leg) {
        val world = leg.parent.location.world
        var current = leg.chain.root.toLocation(world)

        for ((i, segment) in leg.chain.segments.withIndex()) {
            val thickness = (leg.chain.segments.size - i - 1) * 0.025
            renderLine(current, segment.position, thickness)
            current = segment.position.toLocation(world)
        }
    }

    override fun renderTarget(location: Location, identifier: Any) {
        location.world.spawnParticle(Particle.REDSTONE, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
    }

    override fun renderSpider(spider: Spider, debug: RenderDebugOptions) {
        renderLeg(spider.leftFrontLeg)
        renderLeg(spider.rightFrontLeg)
        renderLeg(spider.leftBackLeg)
        renderLeg(spider.rightBackLeg)
    }

    override fun clearSpider(spider: Spider) {
    }
}


interface Renderer {
    fun renderSpider(spider: Spider, debug: RenderDebugOptions)
    fun clearSpider(spider: Spider)
    fun renderTarget(location: Location, identifier: Any) {}
    fun clear(identifier: Any) {}
}