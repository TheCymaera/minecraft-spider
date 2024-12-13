package com.heledron.spideranimation.utilities

import org.bukkit.util.Vector
import org.joml.Quaternionf


class KinematicChain(
        val root: Vector,
        val segments: List<ChainSegment>
) {
    fun fabrik(target: Vector) {
        val tolerance = 0.01

        for (i in 0 until 10) {
            fabrikForward(target)
            fabrikBackward()

            if (getEndEffector().distance(target) < tolerance) {
                break
            }
        }
    }

    fun straightenDirection(rotation: Quaternionf) {
        val position = root.clone()
        for (segment in segments) {
            val initDirection = segment.initDirection.clone().rotate(rotation)
            position.add(initDirection.multiply(segment.length))
            segment.position.copy(position)
        }
    }

    fun fabrikForward(newPosition: Vector) {
        val lastSegment = segments.last()
        lastSegment.position.copy(newPosition)

        for (i in segments.size - 1 downTo 1) {
            val previousSegment = segments[i]
            val segment = segments[i - 1]

            moveSegment(segment.position, previousSegment.position, previousSegment.length)
        }
    }

    fun fabrikBackward() {
        moveSegment(segments.first().position, root, segments.first().length)

        for (i in 1 until segments.size) {
            val previousSegment = segments[i - 1]
            val segment = segments[i]

            moveSegment(segment.position, previousSegment.position, segment.length)
        }
    }

    fun moveSegment(point: Vector, pullTowards: Vector, segment: Double) {
        val direction = pullTowards.clone().subtract(point).normalize()
        point.copy(pullTowards).subtract(direction.multiply(segment))
    }

    fun getEndEffector(): Vector {
        return segments.last().position
    }

    fun getVectors(): List<Vector> {
        return segments.mapIndexed { i, segment ->
            val previous = segments.getOrNull(i - 1)?.position ?: root
            segment.position.clone().subtract(previous)
        }
    }

    fun getRotations(pivot: Quaternionf): List<Quaternionf> {
        val vectors = getVectors()

        val firstRotation = Quaternionf()
            .rotationTo(FORWARD_VECTOR.toVector3f(), vectors.first().toVector3f())
            .stripRelativeZ(pivot)

        // get relative rotations
        val rotations = vectors.mapIndexed { i, current ->
            val previous = vectors.getOrNull(i - 1) ?: return@mapIndexed firstRotation

            Quaternionf().rotationTo(previous.toVector3f(), current.toVector3f())
        }

        // cumulate rotations
        for (i in 1 until rotations.size) {
            rotations[i].mul(rotations[i - 1])
        }

        return rotations
    }
}

class ChainSegment(
        var position: Vector,
        var length: Double,
        var initDirection: Vector,
)