package com.heledron.spideranimation.commands

import com.google.gson.JsonSyntaxException
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
import com.heledron.spideranimation.utilities.Serializer
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType

internal fun resolveSymbol(raw: String): Any? {
    val (name, arguments) = parseSymbol(raw) ?: return null
    val symbol = symbolList.values.flatten().find { it.name.equals(name, ignoreCase = true) }

    if (symbol == null) {
        if (arguments != null) {
            val expected = symbolList.values.flatten().joinToString { it.name }
            throw invalid("Unknown symbol \"${name}\", expected one of $expected")
        }
        return null
    }

    return symbol.invoke(arguments)
}

internal fun suggestSymbolsFor(value: Any) =
    symbolList.filterKeys { it(value) }.values.flatten().map { symbol ->
        val arguments = if (symbol.parameters.isEmpty()) "" else " (${symbol.signature})"
        symbol.name to "${describeType(value)}$arguments"
    }

private class SymbolParameter(
    val name: String,
    val default: Any,
)

private class Symbol(
    val name: String,
    val parameters: List<SymbolParameter> = emptyList(),
    val produce: (Map<String, Any>) -> Any,
) {
    val signature: String = parameters.joinToString(", ") { it.name }

    fun invoke(arguments: String?): Any = produce(resolve(arguments))

    private fun resolve(arguments: String?): Map<String, Any> {
        if (arguments.isNullOrBlank()) return parameters.associate { it.name to it.default }

        val supplied = parseArguments(arguments)

        // check for invalid fields
        for (key in supplied.keys) {
            if (parameters.none { it.name.equals(key, ignoreCase = true) }) {
                val expected = if (parameters.isEmpty()) ", it takes no arguments" else ", expected $signature"
                throw invalid("\"$key\" is not an argument of $name${expected}")
            }
        }

        // resolve with defaults
        val resolved = parameters.associate { parameter ->
            val entry = supplied.entries.firstOrNull { it.key.equals(parameter.name, ignoreCase = true) }
            parameter.name to if (entry == null) parameter.default else coerce(parameter, entry.value)
        }
        return resolved
    }

    private fun parseArguments(literal: String): Map<String, Any?> {
        try {
            @Suppress("UNCHECKED_CAST")
            return Serializer.gson.fromJson(literal, Map::class.java) as Map<String, Any?>
        } catch (_: JsonSyntaxException) {
            val expected = parameters.joinToString(", ") { "${it.name}: ${Serializer.serialize(it.default)}" }
            throw invalid("Malformed arguments $literal for $name, expected { $expected }")
        }
    }

    private fun coerce(parameter: SymbolParameter, value: Any?): Any {
        if (value == null) return parameter.default

        val type = parameter.default.javaClass
        try {
            return Serializer.gson.fromJson(Serializer.gson.toJsonTree(value), type)
        } catch (_: Exception) {
            throw invalid("\"${parameter.name}\" must be ${describeType(type)}")
        }
    }

}

private val symbolList: Map<(value: Any) -> Boolean, List<Symbol>> by lazy {
    mapOf(
        { value: Any -> value is DisplayModel } to listOf(
            Symbol("EMPTY") { DisplayModel.empty() },

            Symbol("TORSO_FLAT") { SpiderTorsoModels.FLAT.clone() },
            Symbol("TORSO_BOXY") { SpiderTorsoModels.BOXY.clone() },
            Symbol("TORSO_STEALTH") { SpiderTorsoModels.STEALTH.clone() },

            Symbol("LEG_BASE") { SpiderLegModel.BASE.clone() },
            Symbol("LEG_FEMUR") { SpiderLegModel.FEMUR.clone() },
            Symbol("LEG_TIBIA") { SpiderLegModel.TIBIA.clone() },
            Symbol("LEG_TIP") { SpiderLegModel.TIP.clone() },
        ),

        { value: Any -> value is List<*> && value.all { it is PaletteEntry } } to AnimatedPalettes.entries.map { palette ->
            Symbol("PALETTE_${palette.name}") { palette.palette }
        },

        { value: Any -> value is SpiderOptions } to listOf(
            presetSymbol("HEX_BOT", 4, ::hexBot),
            presetSymbol("QUAD_BOT", 4, ::quadBot),
            presetSymbol("OCTO_BOT", 4, ::octoBot),

            presetSymbol("HEXAPOD", 3, ::hexapod),
            presetSymbol("QUADRUPED", 3, ::quadruped),
            presetSymbol("OCTOPOD", 3, ::octopod),

            presetSymbol("BIPED", 3, ::biped),
        )
    )
}

private fun presetSymbol(
    name: String,
    defaultSegments: Int,
    create: (segmentCount: Int, segmentLength: Double) -> SpiderOptions,
): Symbol = Symbol(
    name = name,
    parameters = listOf(
        SymbolParameter(name = "segments", default = defaultSegments),
        SymbolParameter(name = "length", default = 1.0),
    ),
    produce = { arguments ->
        val segmentCount = arguments["segments"] as Int
        if (segmentCount < 1) throw invalid("\"segments\" must be an integer of at least 1")

        val segmentLength = arguments["length"] as Double
        if (segmentLength < 0.0) throw invalid("\"length\" must be a number of at least 0")

        create(segmentCount, segmentLength)
    },
)

private val SPLIT_REGEX = Regex("""^([A-Za-z_]\w*)\s*(\{.*})?$""", RegexOption.DOT_MATCHES_ALL)
private fun parseSymbol(raw: String): Pair<String, String?>? =
    SPLIT_REGEX.matchEntire(raw.trim())?.let {
        it.groupValues[1] to it.groupValues[2].ifEmpty { null }
    }

private fun invalid(message: String): CommandSyntaxException =
    SimpleCommandExceptionType({ message }).create()