package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.presets.AnimatedPalettes
import com.heledron.spideranimation.spider.presets.SpiderTorsoModels
import com.heledron.spideranimation.utilities.DisplayModel
import com.heledron.spideranimation.utilities.inverse_kinematics.IKJoint3D
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.asin
import kotlin.math.atan2

class PaletteEntry(
    val block: BlockData,
    val brightness: Display.Brightness,
)

class SegmentPlan(
    var length: Double,
    var joint: IKJoint3D,
    var model: DisplayModel = DisplayModel(listOf())
) {
    fun clone() = SegmentPlan(length, joint, model.clone())
}

class LegPlan(
    var attachmentPosition: Vector,
    var restPosition: Vector,
    var segments: List<SegmentPlan>,
    val restOrientation: Quaternionf = restOrientationFor(attachmentPosition, restPosition),
)

class BodyPlan {
    var scale = 1.0
    var legs = emptyList<LegPlan>()

    var bodyModel = DisplayModel.empty()

    var eyePalette: List<PaletteEntry> = AnimatedPalettes.CYAN_EYES.palette
    var blinkingPalette: List<PaletteEntry> = AnimatedPalettes.CYAN_BLINKING_LIGHTS.palette

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

private fun aimOrientation(direction: Vector): Quaternionf {
    val d = direction.clone().normalize()

    // Inverse of rotationYXZ(yaw, pitch, 0) applied to the chain axis:
    //   rotationYXZ(y, x, 0) * (0, 0, 1) == (cos x sin y, -sin x, cos x cos y)
    val yaw = atan2(d.x, d.z)
    val pitch = -asin(d.y.coerceIn(-1.0, 1.0))

    return Quaternionf().rotationYXZ(yaw.toFloat(), pitch.toFloat(), 0f)
}

private fun restOrientationFor(attachment: Vector, rest: Vector): Quaternionf {
    val direction = rest.clone().subtract(attachment)
    return if (direction.lengthSquared() < 1e-9) Quaternionf() else aimOrientation(direction)
}