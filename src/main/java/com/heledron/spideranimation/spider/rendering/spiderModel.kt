package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.utilities.*
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector4f

fun targetModel(
    location: Location
) = blockModel(
    location = location,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

fun spiderModel(spider: Spider): Model {
    val model = Model()

    // render body
    for ((index, piece) in spider.options.bodyPlan.bodyModel.pieces.withIndex()) {
        val id = "body" to index

        val transform = Matrix4f().rotate(Quaternionf(spider.orientation))
        model.add(id, pieceToModel(spider, spider.position, piece, transform))
    }


    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        val chain = leg.chain

        for ((segmentIndex, rotation) in chain.getRotations(Quaternionf(spider.orientation)).withIndex()) {
            val segmentPlan = spider.options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue

            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root

            for ((pieceIndex, piece) in segmentPlan.model.pieces.withIndex()) {
                val id = legIndex to segmentIndex to pieceIndex
                val transform = Matrix4f().rotate(rotation)
                model.add(id, pieceToModel(spider, parent, piece, transform))
            }

        }
    }

    return model
}



fun pieceToModel(
    spider: Spider,
    position: Vector,
    piece: BlockDisplayModelPiece,
    transformation: Matrix4f,
//    cloakID: Any
) = blockModel(
    location = position.toLocation(spider.world),
    init = {
        it.teleportDuration = 1
        it.interpolationDuration = 1
        it.brightness = piece.brightness
    },
    update = {
        val transform = transformation.mul(piece.transform)
        it.applyTransformationWithInterpolation(transform)

        it.block = if (!piece.tags.contains("cloak")) {
            piece.block
        } else {
            val relative = transform.transform(Vector4f(.5f, .5f, .5f, 1f))
            val pieceLocation = spider.position.clone()
            pieceLocation.x += relative.x
            pieceLocation.y += relative.y
            pieceLocation.z += relative.z
            spider.cloak.getPiece(piece, pieceLocation) ?: piece.block
        }
    }
)