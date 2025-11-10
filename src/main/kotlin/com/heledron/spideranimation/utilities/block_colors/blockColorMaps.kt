package com.heledron.spideranimation.utilities.block_colors

import com.google.gson.Gson
import com.heledron.spideranimation.utilities.colors.Oklab
import com.heledron.spideranimation.utilities.colors.toOklab
import com.heledron.spideranimation.utilities.requireResource
import org.bukkit.Color
import org.bukkit.Material

private val blocks = requireResource("block_colors.json")
    .bufferedReader()
    .use { it.readText() }
    .let {
        val colorMap = mutableMapOf<Material, Color>()

        @Suppress("UNCHECKED_CAST")
        val json = Gson().fromJson(it, Map::class.java) as Map<String, List<Int>>

        for ((key, value) in json) {
            val material = Material.matchMaterial("minecraft:$key") ?: continue
            colorMap[material] = Color.fromRGB(value[0], value[1], value[2])
        }

        colorMap
    }
    .apply {
        // replace missing/incorrect colors
        this[Material.GRASS_BLOCK] = this[Material.MOSS_BLOCK]!!
        this[Material.OAK_LEAVES] = this[Material.MOSS_BLOCK]!!.withBrightness(10)
        this[Material.BIRCH_LEAVES] = this[Material.MOSS_BLOCK]!!.withBrightness(8)
        this[Material.SPRUCE_LEAVES] = this[Material.MOSS_BLOCK]!!.withBrightness(6)
        this[Material.WARPED_TRAPDOOR] = this[Material.WARPED_PLANKS]!!
        this[Material.BARREL] = this[Material.SPRUCE_PLANKS]!!
        this[Material.RESPAWN_ANCHOR] = this[Material.CRYING_OBSIDIAN]!!

        // add wood from logs
        for ((material, color) in this.toList()) {
            val woodMaterial = getWoodVariant(material)
            if (woodMaterial != null) this[woodMaterial] = color
        }
    }
    .toMap()

internal val colorToBlock = run {
    val out = mutableMapOf<Color, BlockColorInfo>()

    for (brightness in 15 downTo 0) {
        for ((material, color) in blocks) {
            // skip non occluding blocks
            if (!material.isOccluding) continue

            // skip logs if there is a wood variant
            val woodVariant = getWoodVariant(material)
            if (woodVariant != null && blocks.containsKey(woodVariant)) continue

            // skip spawners
            if (material == Material.SPAWNER || material == Material.TRIAL_SPAWNER) continue


            // skip shulker boxes
            val id = material.idString()
            if (id.endsWith("shulker_box")) continue

            val newColor = color.withBrightness(brightness)
            if (newColor in out) continue

            out[newColor] = BlockColorInfo(
                material = material,
                brightness = brightness,
                rgb = newColor,
                oklab = newColor.toOklab(),
            )
        }
    }

    out.values.toList()
}

internal val blockToColor = run {
    val out = blocks.toMutableMap()

    // infer color of partial blocks from their full block counterparts
    for ((material, color) in blocks) {
        val id = material.idString()

        val variations = listOf(
            id,
            id.replaceEnd("_planks", ""),
            id.replaceEnd("_wood", ""),
            id.replaceEnd("s", "")
        ).flatMap { baseId ->
            listOf(
                "${baseId}_slab",
                "${baseId}_stairs",
                "${baseId}_wall",
                "${baseId}_trapdoor",
                "waxed_$baseId",
            )
        }

        for (variantId in variations) {
            val variantMaterial = Material.matchMaterial(variantId) ?: continue
            if (out.containsKey(variantMaterial)) continue
            out[variantMaterial] = color
//            println("Inferred: ${variantId.padEnd(40)} from $id")
        }
    }

    out[Material.CAMPFIRE] = out[Material.OAK_LOG]!!

    out
}

internal class BlockColorInfo(
    val material: Material,
    val brightness: Int,
    val rgb: Color,
    val oklab: Oklab,
)

private fun String.replaceEnd(suffix: String, with: String): String {
    return if (endsWith(suffix)) {
        substring(0, length - suffix.length) + with
    } else {
        this
    }
}

private fun getWoodVariant(material: Material): Material? {
    val id = material.idString()

    if (id.endsWith("_log")) {
        return Material.matchMaterial(id.replaceEnd("_log", "_wood"))
    }

    if (id.endsWith("_stem")) {
        return Material.matchMaterial(id.replaceEnd("_stem", "_hyphae"))
    }

    return null
}

private fun Material.idString(): String {
    return this.keyOrThrow.toString()
}