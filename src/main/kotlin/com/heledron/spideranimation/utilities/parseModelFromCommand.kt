package com.heledron.spideranimation.utilities

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.joml.Matrix4f


fun parseModelFromCommand(command: String): DisplayModel {
//    /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0.1f,0f,0f,0.15f,0f,0.0427f,0.0288f,0.4922f,0f,-0.0022f,0.5492f,-0.8771f,0f,0f,0f,1f]}...

    val pieces = mutableListOf<BlockDisplayModelPiece>()

    var json = command.substring("/summon block_display ~-0.5 ~ ~-0.5 ".length)

    // convert 1.0f -> 1.0
    json = json.replace(Regex("""(\d*\.*\d+)f"""), "$1")

    val parsed = Gson().fromJson(json, Map::class.java)

    @Suppress("UNCHECKED_CAST")
    for (passenger in parsed["Passengers"] as List<Map<*, *>>) {
        val blockDisplay = passenger["block_state"] as? Map<*, *> ?: throw IllegalArgumentException("Missing block_state")
        val blockName = blockDisplay["Name"] as? String ?: throw IllegalArgumentException("Missing block_state.Name")
        val blockProperties = blockDisplay["Properties"] as? Map<*, *> ?: emptyMap<Any, Any>()

        // convert nbt form ({Name, Properties}) to the string form NAME[key=value,...]
        val blockString = if (blockProperties.isEmpty()) {
            blockName
        } else {
            "$blockName[${blockProperties.entries.joinToString(",") { (key, value) -> "$key=$value" }}]"
        }

        val blockData = try {
            Bukkit.createBlockData(blockString)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid block_state $blockString: ${e.message}")
        }

        val transformation = passenger["transformation"] as? List<Float> ?: throw IllegalArgumentException("Missing transformation")
        val matrix = Matrix4f(
            transformation[0], transformation[4], transformation[8], transformation[12],
            transformation[1], transformation[5], transformation[9], transformation[13],
            transformation[2], transformation[6], transformation[10], transformation[14],
            transformation[3], transformation[7], transformation[11], transformation[15]
        )


        pieces += BlockDisplayModelPiece(
            block = blockData,
            transform = matrix,
            tags = passenger["Tags"] as? List<String> ?: emptyList()
        )
    }

    return DisplayModel(pieces)
}