package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.presets.SpiderTorsoModels
import com.heledron.spideranimation.utilities.DisplayModel
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector

class SegmentPlan(
    var length: Double,
    var initDirection: Vector,
    var model: DisplayModel = DisplayModel(listOf())
) {
    fun clone() = SegmentPlan(length, initDirection.clone(), model.clone())
}

class LegPlan(
    var attachmentPosition: Vector,
    var restPosition: Vector,
    var segments: List<SegmentPlan>,
)

class BodyPlan {
    var scale = 1.0
    var legs = emptyList<LegPlan>()

    var bodyModel = SpiderTorsoModels.EMPTY.model.clone()

    val eyePalette = arrayOf(
//        * Array(3) { Material.DIAMOND_BLOCK },
        * Array(3) { Material.CYAN_SHULKER_BOX },
        Material.CYAN_CONCRETE,
        Material.CYAN_CONCRETE_POWDER,

        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.LIGHT_BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE_POWDER,
    ).map { it.createBlockData() }

    val blinkingPalette = arrayOf(
        * Array(3) { Material.BLACK_SHULKER_BOX to Display.Brightness(0,15) },
        * Array(3) { Material.VERDANT_FROGLIGHT to Display.Brightness(15,15) },
        Material.LIGHT_BLUE_SHULKER_BOX to Display.Brightness(15,15),
        Material.LIGHT_BLUE_CONCRETE to Display.Brightness(15,15),
        Material.LIGHT_BLUE_CONCRETE_POWDER to Display.Brightness(15,15),
    ).map { (block, brightness) -> block.createBlockData() to brightness }

    fun scale(scale: Double) {
        this.scale *= scale
        bodyModel.scale(scale.toFloat())
        legs.forEach {
            it.attachmentPosition.multiply(scale)
            it.restPosition.multiply(scale)
            it.segments.forEach { segment ->
                segment.length *= scale
                segment.model.scale(scale.toFloat())
            }
        }
    }
}