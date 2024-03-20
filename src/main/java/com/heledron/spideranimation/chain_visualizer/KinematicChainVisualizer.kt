package com.heledron.spideranimation.chain_visualizer

import com.heledron.spideranimation.BlockDisplayRenderer
import com.heledron.spideranimation.ChainSegment
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector


class KinematicChainVisualizer(
        val root: Vector,
        val segments: List<ChainSegment>,
        val blockDisplayRenderer: BlockDisplayRenderer
) {
    var iterator = 0
    var prevIterator = 0
    var stage = 0
    var target: Vector? = null

    fun resetIterator() {
        iterator = segments.size - 1
        stage = 0
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

    fun step() {
        val target = target ?: return

        prevIterator = iterator

        if (stage == 0) {
            val previousSegment = segments.getOrNull(iterator + 1)
            val segment = segments[iterator]

            if (previousSegment == null) {
                segment.position.copy(target)
            } else {
                fabrik_moveSegment(segment.position, previousSegment.position, previousSegment.length)
            }

            if (iterator == 0) {
                stage = 1
            } else {
                iterator--
            }
        } else {
            // backward
            val previousSegmentPos = segments.getOrNull(iterator - 1)?.position ?: root
            val segment = segments[iterator]

            fabrik_moveSegment(segment.position, previousSegmentPos, segment.length)

            if (iterator == segments.size - 1) {
                stage = 0
            } else {
                iterator++
            }
        }
    }

    fun straighten(target: Vector) {
        resetIterator()

        val direction = target.clone().subtract(root).normalize()
        direction.y = 0.5
        direction.normalize()

        val position = root.clone()
        for (segment in segments) {
            position.add(direction.clone().multiply(segment.length))
            segment.position.copy(position)
        }
    }

    fun fabrik_moveSegment(point: Vector, pullTowards: Vector, segment: Double) {
        val direction = pullTowards.clone().subtract(point).normalize()
        point.copy(pullTowards).subtract(direction.multiply(segment))
    }


    fun render(world: World, renderAll: Boolean) {
        var root = this.root.toLocation(world)

        // up vector is the cross product of the y-axis and the end-effector direction
        val direction = this.segments.last().position.clone().subtract(root.toVector())
        val upVector = direction.clone().crossProduct(Vector(0, 1, 0))

        for ((i, segment) in this.segments.withIndex()) {
            val needsUpdate = renderAll || i == this.prevIterator

            if (needsUpdate) {
                val thickness = (this.segments.size - i) * 1.5f/16f

                val vector = segment.position.clone().subtract(root.toVector())
                if (!vector.isZero) vector.normalize().multiply(segment.length)

                val pos = segment.position.clone().subtract(vector.clone()).toLocation(world)

                // val oldLocation = BlockDisplayRenderer.legSegments.displays[segment]?.location

                blockDisplayRenderer.renderSegment(pos, segment, thickness, upVector).apply {
                    this.interpolationDuration = 4
                    this.teleportDuration = 4
                    this.brightness = Display.Brightness(0, 15)
                }
            }

            root = segment.position.toLocation(world)
        }

        val targetLocation = this.target?.toLocation(world) ?: this.root.toLocation(world)
        blockDisplayRenderer.renderTarget(targetLocation, this.target != null, this)
    }

    fun unRender() {
        blockDisplayRenderer.targets.clear(this)
        for (segment in this.segments) blockDisplayRenderer.legSegments.clear(segment)
    }

    companion object {
        fun create(segments: Int, length: Double, root: Vector, renderer: BlockDisplayRenderer): KinematicChainVisualizer {
            val segmentList = (0 until segments).map { ChainSegment(root.clone(), length) }
            return KinematicChainVisualizer(root.clone(), segmentList, renderer).apply { reset() }
        }
    }
}