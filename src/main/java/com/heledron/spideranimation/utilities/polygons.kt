package com.heledron.spideranimation.utilities

import org.joml.Vector2d

fun pointInPolygon(point: Vector2d, polygon: List<Vector2d>): Boolean {
    // count intersections
    var count = 0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]

        if (a.y <= point.y && b.y > point.y || b.y <= point.y && a.y > point.y) {
            val slope = (b.x - a.x) / (b.y - a.y)
            val intersect = a.x + (point.y - a.y) * slope
            if (intersect < point.x) count++
        }
    }

    return count % 2 == 1
}

fun nearestPointInPolygon(point: Vector2d, polygon: List<Vector2d>): Vector2d {
    var closest = polygon[0]
    var closestDistance = point.distance(closest)

    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]

        val closestOnLine = nearestPointOnClampedLine(point, a, b) ?: continue
        val distance = point.distance(closestOnLine)

        if (distance < closestDistance) {
            closest = closestOnLine
            closestDistance = distance
        }
    }

    return closest
}

fun nearestPointOnClampedLine(point: Vector2d, a: Vector2d, b: Vector2d): Vector2d {
    val ap = Vector2d(point.x - a.x, point.y - a.y)
    val ab = Vector2d(b.x - a.x, b.y - a.y)

    val dotProduct = ap.dot(ab)
    val lengthAB = a.distance(b)

    val t = dotProduct / (lengthAB * lengthAB)

    // Ensure the nearest point lies within the line segment
    val tClamped = t.coerceIn(0.0, 1.0)

    val nearestX = a.x + tClamped * ab.x
    val nearestY = a.y + tClamped * ab.y

    return Vector2d(nearestX, nearestY)
}