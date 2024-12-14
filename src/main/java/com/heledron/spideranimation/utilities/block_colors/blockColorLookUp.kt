package com.heledron.spideranimation.utilities.block_colors

import com.heledron.spideranimation.utilities.Serializer.gson
import com.heledron.spideranimation.utilities.currentPlugin
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import kotlin.math.pow
import kotlin.math.sqrt

private fun String.parseJSONColors(): Map<Material, Color> {
    val colorMap = mutableMapOf<Material, Color>()

    @Suppress("UNCHECKED_CAST")
    val json = gson.fromJson(this, Map::class.java) as Map<String, List<Int>>

    for ((key, value) in json) {
        val material = Material.matchMaterial("minecraft:$key") ?: continue
        colorMap[material] = Color.fromRGB(value[0], value[1], value[2])
    }

    return colorMap
}

private val blocks = currentPlugin.getResource("block_colors.json")
    ?.bufferedReader()
    ?.use { it.readText() }
    ?.parseJSONColors()
    ?: throw IllegalStateException("Failed to load block_colors.json")

private val blocksWithBrightness = mutableMapOf<Color, Pair<Material,Int>>().apply {
    for (brightness in 15 downTo 0) {
        for ((material, color) in blocks) {
            if (!material.isOccluding) continue

            val newColor = Color.fromRGB(
                (color.red * brightness.toDouble() / 15).toInt(),
                (color.green * brightness.toDouble() / 15).toInt(),
                (color.blue * brightness.toDouble() / 15).toInt(),
            )

            if (newColor in this) continue

            this[newColor] = material to brightness
        }
    }


    for ((color, material) in this) {
        val id = material.first.key.toString()

        // replace log with wood
        if (id.endsWith("_log")) {
            val woodMaterial = Material.matchMaterial(id.replaceEnd("_log", "_wood")) ?: continue
            this[color] = woodMaterial to material.second
        }
    }
}

private val blockToColor = blocks.toMutableMap().apply {
    this[Material.GRASS_BLOCK] = this[Material.MOSS_BLOCK]!!
    this[Material.MOSS_CARPET] = this[Material.MOSS_BLOCK]!!

    this[Material.WARPED_TRAPDOOR] = this[Material.WARPED_PLANKS]!!

    this[Material.BARREL] = this[Material.SPRUCE_PLANKS]!!

    // replace partial blocks with their full block counterparts
    for (material in Material.entries) {
        if (!material.isBlock) continue
        val id = material.key.toString()

        if (this.containsKey(material)) continue

        val fullBlockName = id
            .replaceEnd("_slab", "")
            .replaceEnd("_stairs", "")
            .replaceEnd("_wall", "")
            .replaceEnd("_trapdoor", "")
            .replace("waxed_", "")

        if (fullBlockName == id) continue

        val fullBlockMaterial =
            Material.matchMaterial(fullBlockName + "_planks") ?:
            Material.matchMaterial(fullBlockName) ?:
            Material.matchMaterial(fullBlockName + "s") ?:
            Material.matchMaterial(fullBlockName + "_wood")

        this[material] = this[fullBlockMaterial] ?: continue
    }
}

fun getColorFromBlock(block: BlockData): Color? {
    return blockToColor[block.material]
}

class MatchInfo(
    val block: BlockData,
    val blockColor: Color,
    val distance: Double,
    val brightness: Int,
)

fun getBestMatchFromColor(color: Color, allowCustomBrightness: Boolean): MatchInfo {
    val map = if (allowCustomBrightness) blocksWithBrightness else blocksWithBrightness.filterValues { it.second == 15 }

    val bestMatch = map.minBy { it.key.distanceTo(color) }
    return MatchInfo(bestMatch.value.first.createBlockData(), bestMatch.key, color.distanceTo(bestMatch.key), bestMatch.value.second)
}

private fun String.replaceEnd(suffix: String, with: String): String {
    return if (endsWith(suffix)) {
        substring(0, length - suffix.length) + with
    } else {
        this
    }
}

private fun Color.distanceTo(other: Color): Double {
    return sqrt(
        (red - other.red).toDouble().pow(2) +
                (green - other.green).toDouble().pow(2) +
                (blue - other.blue).toDouble().pow(2)
    )
}