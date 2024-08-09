package com.heledron.spideranimation

import org.bukkit.util.Vector


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

    fun straightenDirection(direction: Vector) {
        direction.normalize()
//        val direction = target.clone().subtract(root).normalize()
        val position = root.clone()
        for (segment in segments) {
            position.add(direction.clone().multiply(segment.length))
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
}

class ChainSegment(
        var position: Vector,
        var length: Double
)