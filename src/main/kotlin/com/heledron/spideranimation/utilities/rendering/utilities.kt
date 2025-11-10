package com.heledron.hologram.utilities.rendering

import com.heledron.spideranimation.utilities.rendering.RenderEntity
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Matrix4f


fun Display.interpolateTransform(transformation: Transformation) {
    if (this.transformation == transformation) return
    this.transformation = transformation
    this.interpolationDelay = 0
}

fun Display.interpolateTransform(matrix: Matrix4f) {
    val oldTransformation = this.transformation
    setTransformationMatrix(matrix)

    if (oldTransformation == this.transformation) return
    this.interpolationDelay = 0
}


fun renderBlock(
    location: Location,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = RenderEntity(
    clazz = BlockDisplay::class.java,
    location = location,
    init = init,
    update = update
)

fun renderBlock(
    world: World,
    position: Vector,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = RenderEntity(
    clazz = BlockDisplay::class.java,
    location = position.toLocation(world),
    init = init,
    update = update,
)

fun renderText(
    location: Location,
    init: (TextDisplay) -> Unit = {},
    update: (TextDisplay) -> Unit = {},
) = RenderEntity(
    clazz = TextDisplay::class.java,
    location = location,
    init = init,
    update = update
)

fun renderText(
    world: World,
    position: Vector,
    init: (TextDisplay) -> Unit = {},
    update: (TextDisplay) -> Unit = {},
) = renderText(
    location = position.toLocation(world),
    init = init,
    update = update
)


fun renderItem(
    location: Location,
    init: (ItemDisplay) -> Unit = {},
    update: (ItemDisplay) -> Unit = {},
) = RenderEntity(
    clazz = ItemDisplay::class.java,
    location = location,
    init = init,
    update = update
)

fun renderItem(
    world: World,
    position: Vector,
    init: (ItemDisplay) -> Unit = {},
    update: (ItemDisplay) -> Unit = {},
) = renderItem(
    location = position.toLocation(world),
    init = init,
    update = update
)