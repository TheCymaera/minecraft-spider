package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.presets.SpiderTorsoModels
import com.heledron.spideranimation.spider.rendering.BlockDisplayModelPiece
import com.heledron.spideranimation.spider.rendering.DisplayModel
import com.heledron.spideranimation.utilities.FORWARD_VECTOR
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f

class SegmentPlan(
    var length: Double,
    var initDirection: Vector,
    var model: DisplayModel
) {
    fun clone() = SegmentPlan(length, initDirection.clone(), model.clone())

    companion object {
        fun tapered(segmentCount: Int, segmentLength: Double, rootThickness: Double, tipThickness: Double) = List(segmentCount) {
            val fraction = it.toDouble() / (segmentCount - 1)

            val thickness = rootThickness + (tipThickness - rootThickness) * fraction

            SegmentPlan(segmentLength, FORWARD_VECTOR, createDefaultModel(segmentLength, thickness))
        }

        fun createDefaultModel(length: Double, thickness: Double) = DisplayModel(listOf(BlockDisplayModelPiece(
            block = Material.NETHERITE_BLOCK.createBlockData(),
            transform = Matrix4f()
                .scale(thickness.toFloat(), thickness.toFloat(), length.toFloat())
                .translate(-.5f,-.5f,.0f),
            brightness = Display.Brightness(0, 15),
            tags = listOf("cloak")
        )))
    }
}

class LegPlan(
    var attachmentPosition: Vector,
    var restPosition: Vector,
    var segments: List<SegmentPlan>,
)

class BodyPlan {
    var scale = 1.0
    var legs = emptyList<LegPlan>()

    var bodyModel = SpiderTorsoModels.NONE.model.clone()

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

    fun addLegPair(root: Vector, rest: Vector, segments: List<SegmentPlan>) {
        legs += LegPlan(Vector( root.x, root.y, root.z), Vector( rest.x, rest.y, rest.z), segments)
        legs += LegPlan(Vector(-root.x, root.y, root.z), Vector(-rest.x, rest.y, rest.z), segments.map { it.clone() })
    }

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