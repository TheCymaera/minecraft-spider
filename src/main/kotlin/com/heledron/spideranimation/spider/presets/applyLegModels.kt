package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.DisplayModel
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.joml.Matrix4f


private fun createDefaultModel(block: BlockData, length: Double, thickness: Double) = DisplayModel(listOf(BlockDisplayModelPiece(
    block = block,
    transform = Matrix4f()
        .scale(thickness.toFloat(), thickness.toFloat(), length.toFloat())
        .translate(-.5f,-.5f,.0f),
    brightness = Display.Brightness(0, 15),
    tags = listOf("cloak")
)))

fun applyEmptyLegModel(bodyPlan: BodyPlan) {
    for (leg in bodyPlan.legs) {
        for (segment in leg.segments) {
            segment.model = DisplayModel.empty()
        }
    }
}

fun applyLineLegModel(bodyPlan: BodyPlan, block: BlockData) {
    val rootThickness = 1.0/16 * 4.5
    val tipThickness = 1.0/16 * 1.5

    for (leg in bodyPlan.legs) {
        for ((index, segment) in leg.segments.withIndex()) {
            val fraction = index.toDouble() / (leg.segments.size - 1)
            val thickness = rootThickness + (tipThickness - rootThickness) * fraction
            segment.model = createDefaultModel(block, segment.length, thickness)
        }
    }
}

fun applyMechanicalLegModel(bodyPlan: BodyPlan) {
    for (leg in bodyPlan.legs) {
        val visibleSegments = leg.segments.filter { it.length > 0.0 }

        var modelIndex = 0
        for (segment in leg.segments) {
            if (segment.length <= 0.0) {
                segment.model = DisplayModel.empty()
                continue
            }

            val model = when (modelIndex) {
                0 -> SpiderLegModel.BASE
                visibleSegments.size - 2 -> SpiderLegModel.TIBIA
                visibleSegments.size - 1 -> SpiderLegModel.TIP
                else -> SpiderLegModel.FEMUR
            }

            segment.model = model.clone().scale(1f, 1f, segment.length.toFloat())
            modelIndex++
        }
    }
}