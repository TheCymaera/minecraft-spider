package com.heledron.spideranimation.spider.rendering

import com.heledron.hologram.utilities.rendering.interpolateTransform
import com.heledron.hologram.utilities.rendering.renderBlock
import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.deprecated.centredTransform
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Vector4f

fun renderTarget(
    location: Location
) = renderBlock(
    location = location,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

fun renderSpider(spider: Spider): RenderGroup {
    val group = RenderGroup()

    val transform = Matrix4f().rotate(spider.orientation)
    group[spider.body] = renderModel(spider, spider.position, spider.options.bodyPlan.bodyModel, transform)


    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        val chain = leg.chain

        val pivot = spider.gait.legChainPivotMode.get(spider)
        for ((segmentIndex, rotation) in chain.getRotations(pivot).withIndex()) {
            val segmentPlan = spider.options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue

            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root

            val segmentTransform = Matrix4f().rotate(rotation)
            group[legIndex to segmentIndex] = renderModel(spider, parent, segmentPlan.model, segmentTransform)

        }
    }

    return group
}

private fun renderModel(
    spider: Spider,
    position: Vector,
    model: DisplayModel,
    transformation: Matrix4f
): RenderGroup {
    val group = RenderGroup()

    for ((index, piece) in model.pieces.withIndex()) {
        group[index] = renderModelPiece(spider, position, piece, transformation)
    }

    return group
}


private fun renderModelPiece(
    spider: Spider,
    position: Vector,
    piece: BlockDisplayModelPiece,
    transformation: Matrix4f,
//    cloakID: Any
) = renderBlock(
    location = position.toLocation(spider.world),
    init = {
        it.teleportDuration = 1
        it.interpolationDuration = 1
    },
    update = {
        val transform = Matrix4f(transformation).mul(piece.transform)
        it.interpolateTransform(transform)

        val cloak = if (piece.tags.contains("cloak")) {
            val relative = transform.transform(Vector4f(.5f, .5f, .5f, 1f))
            val piecePosition = position.clone()
            piecePosition.x += relative.x
            piecePosition.y += relative.y
            piecePosition.z += relative.z

            spider.cloak.getPiece(piece, piecePosition, piece.block, piece.brightness)
        } else null

        if (cloak != null) {
            it.block = cloak.first
            it.brightness = cloak.second
        } else {
            it.block = piece.block
            it.brightness = piece.brightness
        }
    }
)