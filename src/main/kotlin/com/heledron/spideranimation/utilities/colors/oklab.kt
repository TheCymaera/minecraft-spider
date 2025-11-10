package com.heledron.spideranimation.utilities.colors

import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.lerpSafely
import org.bukkit.Color
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

class Oklab(
    val l: Float,
    val a: Float,
    val b: Float,
    val alpha: Int,
) {
    fun lerp(other: Oklab, t: Float): Oklab {
        return Oklab(
            l = l.lerp(other.l, t),
            a = a.lerp(other.a, t),
            b = b.lerp(other.b, t),
            alpha = alpha.lerpSafely(other.alpha, t),
        )
    }

    fun toRGB(): Color {
        val L = (l * 0.99999999845051981432 +
                0.39633779217376785678 * a +
                0.21580375806075880339 * b).pow(3);
        val M = (l * 1.0000000088817607767 -
                0.1055613423236563494 * a -
                0.063854174771705903402 * b).pow(3);
        val S = (l * 1.0000000546724109177 -
                0.089484182094965759684 * a -
                1.2914855378640917399 * b).pow(3);

        val r = +4.076741661347994 * L -
                3.307711590408193 * M +
                0.230969928729428 * S
        val g = -1.2684380040921763 * L +
                2.6097574006633715 * M -
                0.3413193963102197 * S
        val b = -0.004196086541837188 * L -
                0.7034186144594493 * M +
                1.7076147009309444 * S

        return Color.fromARGB(
            alpha,
            (r.coerceIn(0.0, 1.0) * 255).toInt(),
            (g.coerceIn(0.0, 1.0) * 255).toInt(),
            (b.coerceIn(0.0, 1.0) * 255).toInt(),
        )
    }
}

fun Oklab.distanceTo(other: Oklab): Float {
    return sqrt((l - other.l).pow(2) + (a - other.a).pow(2) + (b - other.b).pow(2))
}

fun Color.toOklab(): Oklab {
    val r = this.red / 255.0;
    val g = this.green / 255.0;
    val b = this.blue / 255.0;

    val L = cbrt(
        0.41222147079999993 * r + 0.5363325363 * g + 0.0514459929 * b
    )
    val M = cbrt(
        0.2119034981999999 * r + 0.6806995450999999 * g + 0.1073969566 * b
    );
    val S = cbrt(
        0.08830246189999998 * r + 0.2817188376 * g + 0.6299787005000002 * b
    )

    return Oklab(
        l = (0.2104542553 * L + 0.793617785 * M - 0.0040720468 * S).toFloat(),
        a = (1.9779984951 * L - 2.428592205 * M + 0.4505937099 * S).toFloat(),
        b = (0.0259040371 * L + 0.7827717662 * M - 0.808675766 * S).toFloat(),
        alpha = this.alpha,
    )
}