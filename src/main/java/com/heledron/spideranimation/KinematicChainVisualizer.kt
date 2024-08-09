package com.heledron.spideranimation

import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import java.io.Closeable


class KinematicChainVisualizer(
    val root: Location,
    val segments: List<ChainSegment>
): Closeable {
    enum class Stage {
        Backwards,
        Forwards
    }

    val interruptions = mutableListOf<() -> Unit>()
    var iterator = 0
    var previous: Triple<Stage, Int, List<ChainSegment>>? = null
    var stage = Stage.Forwards
    var target: Location? = null

    var detailed = false
    set(value) {
        field = value
        interruptions.clear()
        render()
    }

    val renderer = ModelRenderer()
    override fun close() {
        renderer.close()
    }

    init {
        reset()
        render()
    }

    companion object {
        fun create(segments: Int, length: Double, root: Location): KinematicChainVisualizer {
            val segmentList = (0 until segments).map { ChainSegment(root.toVector(), length) }
            return KinematicChainVisualizer(root.clone(), segmentList)
        }
    }

    fun resetIterator() {
        interruptions.clear()
        iterator = segments.size - 1
        previous = null
        stage = Stage.Forwards
    }

    fun reset() {
        resetIterator()

        target = null

        val direction = Vector(0, 1, 0)
        val rotAxis = Vector(1, 0, -1)
        val rotation = -0.2
        val pos = root.toVector()
        for (segment in segments) {
            direction.rotateAroundAxis(rotAxis, rotation)
            pos.add(direction.clone().multiply(segment.length))
            segment.position.copy(pos)
        }

        render()
    }

    fun step() {
        if (interruptions.isNotEmpty()) {
            interruptions.removeAt(0)()
            return
        }

        val target = target?.toVector() ?: return

        previous = Triple(stage, iterator, segments.map { ChainSegment(it.position.clone(), it.length) })

        if (stage == Stage.Forwards) {
            val segment = segments[iterator]
            val nextSegment = segments.getOrNull(iterator + 1)

            if (nextSegment == null) {
                segment.position.copy(target)
            } else {
                fabrik_moveSegment(segment.position, nextSegment.position, nextSegment.length)
            }

            if (iterator == 0) stage = Stage.Backwards
            else iterator--
        } else {
            val segment = segments[iterator]
            val prevPosition = segments.getOrNull(iterator - 1)?.position ?: root.toVector()

            fabrik_moveSegment(segment.position, prevPosition, segment.length)

            if (iterator == segments.size - 1) stage = Stage.Forwards
            else iterator++
        }

        render()
    }

    fun straighten(target: Vector) {
        resetIterator()

        val direction = target.clone().subtract(root.toVector()).normalize()
        direction.y = 0.5
        direction.normalize()

        val position = root.toVector()
        for (segment in segments) {
            position.add(direction.clone().multiply(segment.length))
            segment.position.copy(position)
        }

        render()
    }

    fun fabrik_moveSegment(point: Vector, pullTowards: Vector, segment: Double) {
        val direction = pullTowards.clone().subtract(point).normalize()
        point.copy(pullTowards).subtract(direction.multiply(segment))
    }

    fun render() {
        if (detailed) {
            renderer.render(renderDetailed())
        } else {
            renderer.render(renderNormal())
        }

    }

    private fun renderNormal(): Model {
        val model = Model()

        val previous = previous
        for (i in segments.indices) {
            val thickness = (segments.size - i) * 1.5f/16f

            val list = if (previous == null || i == previous.second) segments else previous.third

            val segment = list[i]
            val prev = list.getOrNull(i - 1)?.position ?: root.toVector()
            val vector = segment.position.clone().subtract(prev)
            if (!vector.isZero) vector.normalize().multiply(segment.length)
            val location = segment.position.clone().subtract(vector.clone()).toLocation(root.world!!)

            model.add(i, lineModel(
                location = location,
                vector = vector,
                thickness = thickness,
                interpolation = 3,
                update = {
                    it.brightness = Display.Brightness(0, 15)
                    it.block = Material.NETHERITE_BLOCK.createBlockData()
                }
            ))
        }

        return model
    }

    private fun renderDetailed(subStage: Int = 0): Model {
        val model = Model()

        val previous = previous

        var renderedSegments = segments

        if (previous != null) run arrow@{
            val (stage, iterator, segments) = previous

            val arrowStart = if (stage == Stage.Forwards)
                segments.getOrNull(iterator + 1)?.position else
                segments.getOrNull(iterator - 1)?.position ?: root.toVector()

            if (arrowStart == null) return@arrow
            renderedSegments = segments

            if (subStage == 0) {
                interruptions += { renderDetailed(1) }
                interruptions += { renderDetailed(2) }
                interruptions += { renderDetailed(3) }
                interruptions += { renderDetailed(4) }
            }

            // stage 0: subtract vector
            val arrow = segments[iterator].position.clone().subtract(arrowStart)

            // stage 1: normalise vector
            if (subStage >= 1) arrow.normalize()

            // stage 2: multiply by length
            if (subStage >= 2) arrow.multiply(segments[iterator].length)

            // stage 3: move segment
            if (subStage >= 3) renderedSegments = this.segments

            // stage 4: hide arrow
            if (subStage >= 4) return@arrow


            val crossProduct = if (arrow == UP_VECTOR) Vector(0, 0, 1) else
                arrow.clone().crossProduct(UP_VECTOR).normalize()

            val arrowCenter = arrowStart.clone()
                .add(arrow.clone().multiply(0.5))
                .add(crossProduct.rotateAroundAxis(arrow, Math.toRadians(-90.0)).multiply(.5))

            model.add("arrow_length", textModel(
                location = arrowCenter.toLocation(root.world!!),
                text = String.format("%.2f", arrow.length()),
                interpolation = 3,
            )
            )

            model.add("arrow", arrowTemplate(
                location = arrowStart.toLocation(root.world!!),
                vector = arrow,
                thickness = .101f,
                interpolation = 3,
            ))
        }

        model.add("root", pointTemplate(root, Material.DIAMOND_BLOCK))

        for (i in renderedSegments.indices) {
            val segment = renderedSegments[i]
            model.add("p$i", pointTemplate(segment.position.toLocation(root.world!!), Material.EMERALD_BLOCK))

            val prev = renderedSegments.getOrNull(i - 1)?.position ?: root.toVector()

            val (a,b) = prev to segment.position

            model.add(i, lineModel(
                location = a.toLocation(root.world!!),
                vector = b.clone().subtract(a),
                thickness = .1f,
                interpolation = 3,
                update = {
                    it.brightness = Display.Brightness(0, 15)
                    it.block = Material.BLACK_STAINED_GLASS.createBlockData()
                }
            ))
        }

        return model
    }
}

fun pointTemplate(location: Location, block: Material) = blockModel(
    location = location,
    init = {
        it.block = block.createBlockData()
        it.teleportDuration = 3
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.26f, .26f, .26f)
    }
)

fun arrowTemplate(
    location: Location,
    vector: Vector,
    thickness: Float,
    interpolation: Int
): Model {
    val line = lineModel(
        location = location,
        vector = vector,
        thickness = thickness,
        interpolation = interpolation,
        init = {
            it.block = Material.GOLD_BLOCK.createBlockData()
            it.brightness = Display.Brightness(0, 15)
        },
    )

    val tipLength = 0.5
    val tip = location.clone().add(vector)
    val crossProduct = if (vector == UP_VECTOR) Vector(0, 0, 1) else
        vector.clone().crossProduct(UP_VECTOR).normalize().multiply(tipLength)

    val tipDirection = vector.clone().normalize().multiply(-tipLength)
    val tipRotation = 30.0


    val top = lineModel(
        location = tip,
        vector = tipDirection.clone().rotateAroundAxis(crossProduct, Math.toRadians(tipRotation)),
        thickness = thickness,
        interpolation = interpolation,
        init = {
            it.block = Material.GOLD_BLOCK.createBlockData()
            it.brightness = Display.Brightness(0, 15)
        },
    )

    val bottom = lineModel(
        location = tip,
        vector = tipDirection.clone().rotateAroundAxis(crossProduct, Math.toRadians(-tipRotation)),
        thickness = thickness,
        interpolation = interpolation,
        init = {
            it.block = Material.GOLD_BLOCK.createBlockData()
            it.brightness = Display.Brightness(0, 15)
        },
    )

    return Model(
        "line" to line,
        "top" to top,
        "bottom" to bottom,
    )

}
