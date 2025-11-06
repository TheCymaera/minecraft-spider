package com.heledron.spideranimation.kinematic_chain_visualizer

import com.heledron.hologram.utilities.rendering.interpolateTransform
import com.heledron.hologram.utilities.rendering.renderBlock
import com.heledron.hologram.utilities.rendering.renderText
import com.heledron.spideranimation.spider.configuration.SegmentPlan
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.deprecated.centredTransform
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.pitch
import com.heledron.spideranimation.utilities.maths.toRadians
import com.heledron.spideranimation.utilities.maths.yaw
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import com.heledron.spideranimation.utilities.rendering.RenderItem
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.io.Closeable
import kotlin.math.sqrt


class KinematicChainVisualizer(
    val world: World,
    val root: Vector,
    val segments: List<ChainSegment>,
    val segmentPlans: List<SegmentPlan>,
    val straightenRotation: Float
) {
    enum class Stage {
        Backwards,
        Forwards
    }

    var iterator = 0
    var previous: Triple<Stage, Int, List<ChainSegment>>? = null
    var stage = Stage.Forwards
    var target: Vector? = null

    var detailed = false
    set(value) {
        field = value
        subStage = 0
    }

    init {
        reset()
    }

    companion object {
        fun create(
            segmentPlans: List<SegmentPlan>,
            root: Vector,
            world: World,
            straightenRotation: Float
        ): KinematicChainVisualizer {
            val segments = segmentPlans.map { ChainSegment(root.clone(), it.length, it.initDirection) }
            return KinematicChainVisualizer(world, root, segments, segmentPlans, straightenRotation)
        }
    }

    fun resetIterator() {
        subStage = 0
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
        val pos = root.clone()
        for (segment in segments) {
            direction.rotateAroundAxis(rotAxis, rotation)
            pos.add(direction.clone().multiply(segment.length))
            segment.position.copy(pos)
        }
    }


    private var subStage = 0;
    fun step() {
        val target = target ?: return

        val isMovingRoot = previous?.first == Stage.Forwards && previous?.second == segments.size - 1

        if (detailed && previous != null && !isMovingRoot) {
            subStage++

            if (subStage <= 4) return
            subStage = 0
        }

        previous = Triple(stage, iterator, segments.map { it.clone() })

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
            val prevPosition = segments.getOrNull(iterator - 1)?.position ?: root

            fabrik_moveSegment(segment.position, prevPosition, segment.length)

            if (iterator == segments.size - 1) stage = Stage.Forwards
            else iterator++
        }
    }

    fun straighten(target: Vector) {
        resetIterator()

        val pivot = Quaternionf()

        val direction = target.clone().subtract(root).normalize()
        val rotation = direction.getRotationAroundAxis(pivot)

        rotation.x += straightenRotation
        val orientation = pivot.rotateYXZ(rotation.y, rotation.x, .0f)

        KinematicChain(root, segments).straightenDirection(orientation)
    }

    fun fabrik_moveSegment(point: Vector, pullTowards: Vector, segment: Double) {
        val direction = pullTowards.clone().subtract(point).normalize()
        point.copy(pullTowards).subtract(direction.multiply(segment))
    }

    fun render(): RenderItem {
        return if (detailed) {
            renderDetailed()
        } else {
            renderNormal()
        }
    }

    private fun renderNormal(): RenderGroup {
        val group = RenderGroup()

        val pivot = Quaternionf()

        val previous = previous
        for (i in segments.indices) {
            val segmentPlan = segmentPlans[i]
            val segment = segments[i]

            val list = if (previous == null || i == previous.second) segments else previous.third

            val prev = list.getOrNull(i - 1)?.position ?: root.clone()
            val vector = segment.position.clone().subtract(prev)
            if (!vector.isZero) vector.normalize().multiply(segment.length)
            val position = segment.position.clone().subtract(vector.clone())

            val rotation = KinematicChain(root, list).getRotations(pivot)[i]
            val transform = Matrix4f().rotate(rotation)

            for (piece in segmentPlan.model.pieces) {
                group[i to piece] = renderBlock(
                    world = world,
                    position = position,
                    init = {
                        it.teleportDuration = 3
                        it.interpolationDuration = 3
                    },
                    update = {
                        val pieceTransform = Matrix4f(transform).mul(piece.transform)
                        it.interpolateTransform(pieceTransform)
                        it.block = piece.block
                        it.brightness = piece.brightness
                    }
                )
            }
        }

        return group
    }

    private fun renderDetailed(): RenderGroup {
        val group = RenderGroup()

        val previous = previous

        var renderedSegments = segments

        if (previous != null) run arrow@{
            val (stage, iterator, segments) = previous

            val arrowStart = if (stage == Stage.Forwards)
                segments.getOrNull(iterator + 1)?.position else
                segments.getOrNull(iterator - 1)?.position ?: root

            if (arrowStart == null) return@arrow
            renderedSegments = segments

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


            val crossProduct = if (arrow == UP_VECTOR) FORWARD_VECTOR else
                arrow.clone().crossProduct(UP_VECTOR).normalize()

            val arrowCenter = arrowStart.clone()
                .add(arrow.clone().multiply(0.5))
                .add(crossProduct.rotateAroundAxis(arrow, Math.toRadians(-90.0)).multiply(.5))

            group["arrow_length"] = renderText(
                world = world,
                position = arrowCenter,
                init = {
                    it.teleportDuration = 3
                    it.billboard = Display.Billboard.CENTER
                },
                update = {
                    it.text = String.format("%.2f", arrow.length())
                }
            )

            group["arrow"] = renderArrow(
                world = world,
                position = arrowStart,
                length = arrow.length().toFloat(),
                matrix = Matrix4f().rotateYXZ(arrow.yaw(), arrow.pitch(), 90f.toRadians()),
                arrowHeadLength = .4f,
                thickness = .101f,
                blockData = Material.GOLD_BLOCK.createBlockData(),
                interpolation = 3,
            )
        }

        group["root"] = renderPoint(world, root, Material.DIAMOND_BLOCK)

        for (i in renderedSegments.indices) {
            val segment = renderedSegments[i]
            group["p$i"] = renderPoint(world, segment.position, Material.EMERALD_BLOCK)

            val prev = renderedSegments.getOrNull(i - 1)?.position ?: root

            val (a,b) = prev to segment.position

            group[i] = renderLine(
                world = world,
                position = a,
                vector = b.clone().subtract(a),
                thickness = .1f,
                interpolation = 3,
                update = {
                    it.brightness = Display.Brightness(0, 15)
                    it.block = Material.BLACK_STAINED_GLASS.createBlockData()
                }
            )
        }

        return group
    }
}

private fun renderPoint(world: World, position: Vector, block: Material) = renderBlock(
    world = world,
    position = position,
    init = {
        it.block = block.createBlockData()
        it.teleportDuration = 3
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.26f, .26f, .26f)
    }
)


fun renderArrow(
    world: World,
    position: Vector,
    blockData: org.bukkit.block.data.BlockData,
    matrix: Matrix4f,
    length: Float,
    thickness: Float,
    arrowHeadLength: Float,
    arrowHeadRotation: Float = 45f.toRadians(),
    interpolation: Int,
): RenderGroup {
    val group = RenderGroup()
    val zFightingOffset = 0.001f

    // render line
    group[0] = renderBlock(
        world = world,
        position = position,
        init = {
            it.block = blockData
            it.interpolationDuration = interpolation
        },
        update = {
            it.interpolateTransform(
                Matrix4f(matrix)
                    .scale(thickness, thickness, length - thickness * sqrt(2f)) // offset length for arrow head
                    .translate(-.5f, -.5f, 0f)
            )
        }
    )


    // render arrow head
    for (sign in listOf(1, -1)) group[sign] = renderBlock(
        world = world,
        position = position,
        init = {
//            it.block = if (sign == 1) Material.BLACK_CONCRETE.createBlockData() else Material.WHITE_CONCRETE.createBlockData()
            it.block = blockData
            it.interpolationDuration = interpolation
        },
        update = {
            // prevent z-fighting
            val headThickness = thickness + zFightingOffset * (2 + sign)

            it.interpolateTransform(
                Matrix4f(matrix)
                    .translate(0f, 0f, length)
                    .rotateY(180f.toRadians() - arrowHeadRotation * sign)
                    .translate(0f, 0f, -zFightingOffset * sign)
                    .scale(headThickness, headThickness, arrowHeadLength)
                    .translate(-.5f + .5f * sign, -.5f, 0f)
            )
        }
    )

    return group
}


fun setupChainVisualizer(app: ECS) {
    app.onRender {
        for (visualizer in app.query<KinematicChainVisualizer>()) {
            visualizer.render().submit(visualizer)
        }
    }
}