package com.heledron.spideranimation.spider.presets

import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display

enum class AnimatedPalettes(val palette: List<Pair<BlockData, Display.Brightness>>) {
    CYAN_EYES(arrayOf(
        * Array(3) { Material.CYAN_SHULKER_BOX },
        Material.CYAN_CONCRETE,
        Material.CYAN_CONCRETE_POWDER,

        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.LIGHT_BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE_POWDER,
    ).map { it.createBlockData() to Display.Brightness(15,15) }),

    CYAN_BLINKING_LIGHTS(arrayOf(
        * Array(3) { Material.BLACK_SHULKER_BOX to Display.Brightness(0,15) },
        * Array(3) { Material.VERDANT_FROGLIGHT to Display.Brightness(15,15) },
        Material.LIGHT_BLUE_SHULKER_BOX to Display.Brightness(15,15),
        Material.LIGHT_BLUE_CONCRETE to Display.Brightness(15,15),
        Material.LIGHT_BLUE_CONCRETE_POWDER to Display.Brightness(15,15),
    ).map { (block, brightness) -> block.createBlockData() to brightness }),


    RED_EYES(arrayOf(
        * Array(3) { Material.RED_SHULKER_BOX },
        Material.RED_CONCRETE,
        Material.RED_CONCRETE_POWDER,

        Material.FIRE_CORAL_BLOCK,
        Material.REDSTONE_BLOCK,
    ).map { it.createBlockData() to Display.Brightness(15,15) }),

    RED_BLINKING_LIGHTS(arrayOf(
        * Array(3) { Material.BLACK_SHULKER_BOX to Display.Brightness(0,15) },
        * Array(3) { Material.PEARLESCENT_FROGLIGHT to Display.Brightness(15,15) },
        Material.RED_TERRACOTTA to Display.Brightness(15,15),
        Material.REDSTONE_BLOCK to Display.Brightness(15,15),
        Material.FIRE_CORAL_BLOCK to Display.Brightness(15,15),
    ).map { (block, brightness) -> block.createBlockData() to brightness }),
}