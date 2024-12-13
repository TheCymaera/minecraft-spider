package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.rendering.BlockDisplayModelPiece
import com.heledron.spideranimation.spider.rendering.DisplayModel
import com.heledron.spideranimation.utilities.matrixFromTransform
import com.heledron.spideranimation.utilities.runCommandSilently
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay

fun parseModelFromCommand(command: String): DisplayModel {
    val world = Bukkit.getWorlds().first()
    val location = Location(world, .0,.0,.0)

    runCommandSilently(
        location = location,
        command = "execute positioned ${location.x + .5} ${location.y} ${location.z + .5} run ${command.trimStart('/')}"
    )

    val pieces = mutableListOf<BlockDisplayModelPiece>()

    val radius = 0.001
    for (entity in world.getNearbyEntities(location, radius, radius, radius)) {
        if (entity !is BlockDisplay) continue

        val transform = matrixFromTransform(entity.transformation)
        pieces += BlockDisplayModelPiece(
            block = entity.block,
            transform = transform,
            brightness = entity.brightness,
            tags = entity.scoreboardTags.toList()
        )

        entity.remove()
    }

    return DisplayModel(pieces)
}