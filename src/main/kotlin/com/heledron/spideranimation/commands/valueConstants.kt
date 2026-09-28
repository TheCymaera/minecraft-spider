package com.heledron.spideranimation.commands

import com.heledron.spideranimation.spider.configuration.PaletteEntry
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.AnimatedPalettes
import com.heledron.spideranimation.spider.presets.SpiderLegModel
import com.heledron.spideranimation.spider.presets.SpiderTorsoModels
import com.heledron.spideranimation.spider.presets.biped
import com.heledron.spideranimation.spider.presets.hexBot
import com.heledron.spideranimation.spider.presets.hexapod
import com.heledron.spideranimation.spider.presets.octoBot
import com.heledron.spideranimation.spider.presets.octopod
import com.heledron.spideranimation.spider.presets.quadBot
import com.heledron.spideranimation.spider.presets.quadruped
import com.heledron.spideranimation.utilities.DisplayModel

internal class ValueConstant(
    val name: String,
    val produce: () -> Any,
)

internal val valueConstants: Map<(value: Any) -> Boolean, List<ValueConstant>> by lazy {
    mapOf(
        { value: Any -> value is DisplayModel } to SpiderTorsoModels.entries.map { model ->
            ValueConstant("TORSO_${model.name}") { model.model.clone() }
        } + listOf(
            ValueConstant("LEG_BASE") { SpiderLegModel.BASE.clone() },
            ValueConstant("LEG_FEMUR") { SpiderLegModel.FEMUR.clone() },
            ValueConstant("LEG_TIBIA") { SpiderLegModel.TIBIA.clone() },
            ValueConstant("LEG_TIP") { SpiderLegModel.TIP.clone() },
        ),

        { value: Any -> value is List<*> && value.all { it is PaletteEntry } } to AnimatedPalettes.entries.map { palette ->
            ValueConstant("PALETTE_${palette.name}") { palette.palette }
        },

        { value: Any -> value is SpiderOptions } to listOf(
            ValueConstant("HEX_BOT") { hexBot(4, 1.0) },
            ValueConstant("QUAD_BOT") { quadBot(4, 1.0) },
            ValueConstant("OCTO_BOT") { octoBot(4, 1.0) },

            ValueConstant("HEXAPOD") { hexapod(3, 1.0) },
            ValueConstant("QUADRUPED") { quadruped(3, 1.0) },
            ValueConstant("OCTOPOD") { octopod(3, 1.0) },

            ValueConstant("BIPED") { biped(3, 1.0) },
        )
    )
}

internal fun findValueConstant(name: String): ValueConstant? =
    valueConstants.values.flatten().find { it.name.equals(name, ignoreCase = true) }

internal fun valueConstantsFor(value: Any): List<ValueConstant> =
    valueConstants.filterKeys { it(value) }.values.flatten()