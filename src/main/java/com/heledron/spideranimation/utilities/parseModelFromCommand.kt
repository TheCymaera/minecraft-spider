package com.heledron.spideranimation.utilities

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