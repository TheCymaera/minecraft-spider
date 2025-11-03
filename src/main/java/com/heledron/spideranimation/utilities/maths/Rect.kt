package com.heledron.spideranimation.utilities.maths

import org.joml.Vector2d

class Rect private constructor(var minX: Double, var minY: Double, var maxX: Double, var maxY: Double) {
    val width; get() = maxX - minX
    val height; get() = maxY - minY

    val dimensions; get() = Vector2d(width, height)

    companion object {
        fun fromMinMax(min: Vector2d, max: Vector2d): Rect {
            return Rect(min.x, min.y, max.x, max.y)
        }

        fun fromCenter(center: Vector2d, dimensions: Vector2d): Rect {
            return Rect(
                center.x - dimensions.x / 2,
                center.y - dimensions.y / 2,
                center.x + dimensions.x / 2,
                center.y + dimensions.y / 2
            )
        }
    }

    fun clone(): Rect {
        return Rect(minX, minY, maxX, maxY)
    }

    fun expand(padding: Double): Rect {
        minX -= padding
        minY -= padding
        maxX += padding
        maxY += padding
        return this
    }

    fun setYCenter(center: Double, height: Double): Rect {
        minY = center - height / 2
        maxY = center + height / 2
        return this
    }

    fun lerp(other: Rect, t: Double): Rect {
        minX = minX.lerp(other.minX, t)
        minY = minY.lerp(other.minY, t)
        maxX = maxX.lerp(other.maxX, t)
        maxY = maxY.lerp(other.maxY, t)
        return this
    }

    fun set(other: Rect): Rect {
        minX = other.minX
        minY = other.minY
        maxX = other.maxX
        maxY = other.maxY
        return this
    }
}