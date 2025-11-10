package com.heledron.spideranimation.utilities.block_colors

import com.heledron.spideranimation.utilities.colors.Oklab
import com.heledron.spideranimation.utilities.colors.distanceTo
import com.heledron.spideranimation.utilities.colors.toOklab
import org.bukkit.Color
import org.bukkit.block.data.BlockData

class BlockColorMatch(
    val block: BlockData,
    val brightness: Int,
)

fun getBlockColor(block: BlockData, brightness: Int): Color? {
    return getBlockColor(block)?.withBrightness(brightness)
}

fun getBlockColor(block: BlockData): Color? {
    return blockToColor[block.material]
}

fun findBlockWithColor(color: Oklab, allowCustomBrightness: Boolean): BlockColorMatch {
    val list = if (allowCustomBrightness) colorToBlock else colorToBlock.filter { it.brightness == 15 }

    val bestMatch = list.minBy { it.oklab.distanceTo(color) }
    return BlockColorMatch(
        block = bestMatch.material.createBlockData(),
        brightness = bestMatch.brightness,
    )
}

fun findBlockWithColor(color: Color, allowCustomBrightness: Boolean): BlockColorMatch {
    val list = if (allowCustomBrightness) colorToBlock else colorToBlock.filter { it.brightness == 15 }

    val bestMatch = list.minBy { it.rgb.distanceTo(color) }
    return BlockColorMatch(
        block = bestMatch.material.createBlockData(),
        brightness = bestMatch.brightness,
    )
}

enum class FindBlockWithColor(val customBrightness: Boolean, val match: (Color) -> BlockColorMatch) {
    RGB(false, { color -> findBlockWithColor(color, false) }),
    RGB_WITH_BRIGHTNESS(true, { color -> findBlockWithColor(color, true) }),
    OKLAB(false, { color -> findBlockWithColor(color.toOklab(), false) }),
    OKLAB_WITH_BRIGHTNESS(true, { color -> findBlockWithColor(color.toOklab(), true) }),
}


internal fun Color.withBrightness(brightness: Int): Color {
    return Color.fromRGB(
        (red * brightness.toDouble() / 15).toInt(),
        (green * brightness.toDouble() / 15).toInt(),
        (blue * brightness.toDouble() / 15).toInt(),
    )
}
