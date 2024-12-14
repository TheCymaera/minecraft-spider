package com.heledron.spideranimation.utilities.block_colors

import com.heledron.spideranimation.utilities.Serializer.gson
import com.heledron.spideranimation.utilities.currentPlugin
import org.bukkit.Color
import org.bukkit.Material

private fun rgb(r: Int, g: Int, b: Int) = Color.fromRGB(r, g, b)

private fun String.parseJSONColors(): Map<String, Color> {
    val colorMap = mutableMapOf<String, Color>()

    @Suppress("UNCHECKED_CAST")
    val json = gson.fromJson(this, Map::class.java) as Map<String, List<Int>>

    for ((key, value) in json) {
        colorMap[key] = rgb(value[0], value[1], value[2])
    }

    return colorMap
}

object ColorMap {
    private val allColors = currentPlugin.getResource("block_colors.json")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?.parseJSONColors()
        ?: throw IllegalStateException("Failed to load block_colors.json")

    val blocks = allColors.mapNotNull { (idString, value) ->
        val material = Material.matchMaterial("minecraft:$idString")
        if (material == null) {
            currentPlugin.logger.warning("Failed to find material for $idString")
            return@mapNotNull null
        }

        material to value
    }.toMap()
}