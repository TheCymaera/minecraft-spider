package com.heledron.spideranimation

import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.plugin.Plugin
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

class DisplayRenderer(val plugin: Plugin) : Renderer {
    val legSegments = DisplayRegistry<ChainSegment>()
    val legTargetPositions = DisplayRegistry<Leg>()
    val triggerZones = DisplayRegistry<Leg>()
    val legRestPositions = DisplayRegistry<Leg>()
    val legRestPositionCenter = DisplayRegistry<Leg>()
    val endEffectors = DisplayRegistry<Leg>()
    val targets = DisplayRegistry<Any>()
    val directions = DisplayRegistry<Spider>()

    override fun renderSpider(spider: Spider, debug: RenderDebugOptions) {
        for (leg in spider.legs) {
            renderLeg(leg, debug)
        }

        renderDirection(spider, debug.spiderDirection)
        renderBones(spider)

        getVisibleTarget(spider)?.let { renderTarget(it, true, spider) } ?: renderTarget(spider.location, false, spider)

        if (spider.didHitGround) {
            runLater(plugin, 1) {
                spider.location.world.playSound(spider.location, Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.0f, .8f)
            }
        }
    }

    override fun clearSpider(spider: Spider) {
        for (leg in spider.legs) {
            for (segment in leg.chain.segments) {
                legSegments.clear(segment)
            }
            legTargetPositions.clear(leg)
            triggerZones.clear(leg)
            legRestPositions.clear(leg)
            legRestPositionCenter.clear(leg)
            endEffectors.clear(leg)
        }

        targets.clear(spider)
        directions.clear(spider)
    }

    fun renderDirection(spider: Spider, render: Boolean) {
        val location = spider.location.clone()
        location.add(spider.location.direction)

        directions.renderBlockIf(spider, location, render) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f
            it.transformation = centredTransform(size, size, size)
        }?.apply {
            this.block = Material.EMERALD_BLOCK.createBlockData()
            this.teleport(location)
        }
    }

    fun renderBones(spider: Spider){
        val location = spider.location.clone()
        location.pitch = 0f

        spider.itemBones.forEach() { itemDisplay ->
            directions.renderItem(spider, location) {
                it.transformation = itemDisplay.transformation
                it.teleportDuration = itemDisplay.teleportDuration
                it.interpolationDuration = itemDisplay.interpolationDuration
                it.viewRange = itemDisplay.viewRange
                it.shadowRadius = itemDisplay.shadowRadius
                it.shadowStrength = itemDisplay.shadowStrength
                it.displayWidth = itemDisplay.displayWidth
                it.displayHeight = itemDisplay.displayHeight
                it.interpolationDelay = itemDisplay.interpolationDelay
                it.billboard = itemDisplay.billboard
                it.glowColorOverride = itemDisplay.glowColorOverride
                it.brightness = itemDisplay.brightness
            }?.apply {
                this.itemStack = itemDisplay.itemStack
                this.itemDisplayTransform = itemDisplay.itemDisplayTransform
                this.teleport(location)
            }
        }
        spider.blockBones.forEach() { blockDisplay ->
            directions.renderBlock(spider, location) {
                it.transformation = blockDisplay.transformation
                it.teleportDuration = blockDisplay.teleportDuration
                it.interpolationDuration = blockDisplay.interpolationDuration
                it.viewRange = blockDisplay.viewRange
                it.shadowRadius = blockDisplay.shadowRadius
                it.shadowStrength = blockDisplay.shadowStrength
                it.displayWidth = blockDisplay.displayWidth
                it.displayHeight = blockDisplay.displayHeight
                it.interpolationDelay = blockDisplay.interpolationDelay
                it.billboard = blockDisplay.billboard
                it.glowColorOverride = blockDisplay.glowColorOverride
                it.brightness = blockDisplay.brightness
            }?.apply {
                this.block = blockDisplay.block
                this.teleport(location)
            }
        }
    }

    fun renderTarget(location: Location, visible: Boolean, identifier: Any) {
        targets.renderBlockIf(identifier, location, visible) {
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
            runLater(plugin, 1) {
                location.world.playSound(location, Sound.BLOCK_NETHERITE_BLOCK_STEP, volume, pitch)
            }
        }
    }

    fun renderLegTargetPosition(leg: Leg, renderDebug: Boolean) {
        val location = leg.targetPosition.toLocation(leg.parent.location.world)

        legTargetPositions.renderBlockIf(leg, location, renderDebug) {
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

        legRestPositions.renderBlockIf(leg, location, renderDebug) {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.05f
            it.transformation = centredTransform(size, ySize.toFloat(), size)
        }?.apply {
            this.block = if (leg.isStranded) Material.COPPER_BLOCK.createBlockData() else Material.GOLD_BLOCK.createBlockData()
            this.teleport(location)
        }

        legRestPositionCenter.renderBlockIf(leg, location, renderDebug) {
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

        triggerZones.renderBlockIf(leg, location, renderDebug) {
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

        endEffectors.renderBlockIf(leg, position, renderDebug) {
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
        return legSegments.renderBlock(segment, location) {
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

            // decompose matrix
             val translation = matrix.getTranslation(Vector3f())
             val rotation = matrix.getUnnormalizedRotation(Quaternionf())
             val scale = matrix.getScale(Vector3f())

             val transform = Transformation(translation, rotation, scale, Quaternionf())

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

    fun renderTarget(location: Location) {
        location.world.spawnParticle(Particle.REDSTONE, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
    }

    override fun renderSpider(spider: Spider, debug: RenderDebugOptions) {
        spider.legs.forEach(::renderLeg)

        getVisibleTarget(spider)?.let { renderTarget(it) }
    }

    override fun clearSpider(spider: Spider) {
    }
}


interface Renderer {
    fun renderSpider(spider: Spider, debug: RenderDebugOptions)
    fun clearSpider(spider: Spider)
}

class DisplayRegistry<T> {
    val blockDisplays = WeakHashMap<T, BlockDisplay>()
    val itemDisplays = WeakHashMap<T, ItemDisplay>()

    init {
        instances.add(this)
    }

    fun renderBlock(identifier: T, location: Location, init: (BlockDisplay) -> Unit): BlockDisplay {
        val entity = blockDisplays.getOrPut(identifier) {
            location.world.spawn(location, BlockDisplay::class.java) {
                init(it)
            }
        }

        entity.teleport(location)
        return entity
    }

    fun renderItem(identifier: T, location: Location, init: (ItemDisplay) -> Unit): ItemDisplay {
        val entity = itemDisplays.getOrPut(identifier) {
            location.world.spawn(location, ItemDisplay::class.java) {
                init(it)
            }
        }

        entity.teleport(location)
        return entity
    }

    fun renderBlockIf(identifier: T, location: Location, condition: Boolean, init: (BlockDisplay) -> Unit): BlockDisplay? {
        if (!condition) {
            blockDisplays[identifier]?.remove()
            blockDisplays.remove(identifier)
            return null
        }

        return renderBlock(identifier, location, init)
    }

    fun renderItemIf(identifier: T, location: Location, condition: Boolean, init: (ItemDisplay) -> Unit): ItemDisplay? {
        if (!condition) {
            itemDisplays[identifier]?.remove()
            itemDisplays.remove(identifier)
            return null
        }

        return renderItem(identifier, location, init)
    }

    fun clear(identifier: T) {
        blockDisplays[identifier]?.remove()
        blockDisplays.remove(identifier)
    }

    fun clearAll() {
        for (entity in blockDisplays.values) entity.remove()
        blockDisplays.clear()
        for (entity in itemDisplays.values) entity.remove()
        itemDisplays.clear()
    }

    companion object {
        val instances = ArrayList<DisplayRegistry<*>>()

        fun clearAll() {
            for (instance in instances) instance.clearAll()
        }
    }
}


fun getVisibleTarget(spider: Spider): Location? {
    val target = spider.behaviour as? TargetBehaviour
    if (target != null && target.visible) {
        return target.target
    }

    return null
}