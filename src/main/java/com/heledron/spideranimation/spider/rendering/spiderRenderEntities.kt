package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.utilities.*
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Vector4f

fun targetRenderEntity(
    location: Location
) = blockRenderEntity(
    location = location,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

fun spiderRenderEntities(spider: Spider): RenderEntityGroup {
    val group = RenderEntityGroup()

    // render body
    for ((index, piece) in spider.options.bodyPlan.bodyModel.pieces.withIndex()) {
        val id = "body" to index

        val transform = Matrix4f().rotate(spider.orientation)
        group.add(id, modelPieceToRenderEntity(spider, spider.position, piece, transform))
    }


    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        val chain = leg.chain

        for ((segmentIndex, rotation) in chain.getRotations(spider.orientation).withIndex()) {
            val segmentPlan = spider.options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue

            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root

            for ((pieceIndex, piece) in segmentPlan.model.pieces.withIndex()) {
                val id = legIndex to segmentIndex to pieceIndex
                val transform = Matrix4f().rotate(rotation)
                group.add(id, modelPieceToRenderEntity(spider, parent, piece, transform))
            }

        }
    }

    return group
}



fun modelPieceToRenderEntity(
    spider: Spider,
    position: Vector,
    piece: BlockDisplayModelPiece,
    transformation: Matrix4f,
//    cloakID: Any
) = blockRenderEntity(
    location = position.toLocation(spider.world),
    init = {
        it.teleportDuration = 1
        it.interpolationDuration = 1
    },
    update = {
        val transform = transformation.mul(piece.transform)
        it.applyTransformationWithInterpolation(transform)

        val cloak = if (piece.tags.contains("cloak")){
            val relative = transform.transform(Vector4f(.5f, .5f, .5f, 1f))
            val pieceLocation = spider.position.clone()
            pieceLocation.x += relative.x
            pieceLocation.y += relative.y
            pieceLocation.z += relative.z

            spider.cloak.getPiece(piece, pieceLocation)
        } else null

        if (cloak != null) {
            it.block = cloak
            it.brightness = null
        } else {
            it.block = piece.block
            it.brightness = piece.brightness
        }
    }
)