package com.heledron.spideranimation.utilities.colors

import com.heledron.spideranimation.utilities.maths.lerpSafely
import net.md_5.bungee.api.ChatColor
import org.bukkit.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


fun Color.blendAlpha(top: Color): Color {
    val bottom = this

    val alpha = top.alpha / 255.0
    val topAlpha = bottom.alpha / 255.0
    val blendedAlpha = alpha + topAlpha * (1 - alpha)
    val r = (top.red * alpha + bottom.red * topAlpha * (1 - alpha)) / blendedAlpha
    val g = (top.green * alpha + bottom.green * topAlpha * (1 - alpha)) / blendedAlpha
    val b = (top.blue * alpha + bottom.blue * topAlpha * (1 - alpha)) / blendedAlpha
    return Color.fromARGB((blendedAlpha * 255).toInt(), r.toInt(), g.toInt(), b.toInt())
}

fun Color.lerpRGB(other: Color, t: Float): Color {
    return Color.fromARGB(
        this.alpha.lerpSafely(other.alpha, t),
        this.red.lerpSafely(other.red, t),
        this.green.lerpSafely(other.green, t),
        this.blue.lerpSafely(other.blue, t),
    )
}

fun Color.value(): Float {
    return ((red + green + blue) / 3f) / 255f
}

fun Color.scaleRGB(value: Float): Color {
    val r = (red * value).toInt().coerceIn(0, 255)
    val g = (green * value).toInt().coerceIn(0, 255)
    val b = (blue * value).toInt().coerceIn(0, 255)
    return Color.fromARGB(alpha, r, g, b)
}

fun Color.scaleAlpha(value: Float): Color {
    val a = (alpha * value).toInt().coerceIn(0, 255)
    return Color.fromARGB(a, red, green, blue)
}

fun Color.toHSV(): Triple<Float, Float, Float> {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)

    val delta = max - min

    val h = when {
        delta == 0f -> 0f
        max == r -> 60 * (((g - b) / delta) % 6)
        max == g -> 60 * ((b - r) / delta + 2)
        max == b -> 60 * ((r - g) / delta + 4)
        else -> error("Unreachable")
    }

    val s = if (max == 0f) 0f else delta / max
    val v = max

    return Triple(h, s, v)
}

typealias ColorGradient = List<Pair<Float, Color>>

fun ColorGradient.interpolate(t: Float, lerpFunction: (Color, Color, Float) -> Color): Color {
    val index = this.indexOfLast { it.first <= t }
    if (index == this.size - 1) return this.last().second
    val start = this[index]
    val end = this[index + 1]
//    return start.second.lerp(end.second, (t - start.first) / (end.first - start.first))
    return lerpFunction(start.second, end.second, (t - start.first) / (end.first - start.first))
}

fun ColorGradient.interpolateRGB(t: Float): Color {
    return interpolate(t) { start, end, fraction -> start.lerpRGB(end, fraction) }
}

fun ColorGradient.interpolateOkLab(t: Float): Color {
    return interpolate(t) { start, end, fraction -> start.lerpOkLab(end, fraction) }
}

fun Color.lerpOkLab(other: Color, t: Float): Color {
    val start = this.toOklab()
    val end = other.toOklab()
    val result = start.lerp(end, t).toRGB()
    return result
}

fun Color.distanceTo(other: Color): Float {
    return sqrt((red - other.red).toFloat().pow(2) + (green - other.green).toFloat().pow(2) + (blue - other.blue).toFloat().pow(2))
}

fun Color.toChatColor(): ChatColor {
    return ChatColor.of(java.awt.Color(red, green, blue))
}

//fun Color.hsvLerp(other: Color, t: Double): Color {
//    val (h1, s1, v1) = this.toHSV()
//    val (h2, s2, v2) = other.toHSV()
//
//    val h = h1.lerp(h2, t)
//    val s = s1.lerp(s2, t)
//    val v = v1.lerp(v2, t)
//    val a = this.alpha.toDouble().lerp(other.alpha.toDouble(), t).toInt()
//
//    return hsv(h, s, v).setAlpha(a)
//}

/**
 * Converts an HSV color to RGB.
 * @param h The hue in degrees. (0 - 360)
 * @param s The saturation as a percentage. (0 - 1)
 * @param v The value as a percentage. (0 - 1)
 * @return The RGB color.
 */
fun hsv(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60) % 2 - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color.fromRGB(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
}