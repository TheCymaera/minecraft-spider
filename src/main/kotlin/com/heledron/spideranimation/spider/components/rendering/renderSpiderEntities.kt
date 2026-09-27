package com.heledron.spideranimation.spider.components.rendering

import com.heledron.spideranimation.utilities.rendering.interpolateTransform
import com.heledron.spideranimation.utilities.rendering.renderBlock
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Vector4f


fun renderSpider(spider: SpiderBody, cloak: Cloak, options: SpiderOptions): RenderGroup {
    val group = RenderGroup()

    val transform = Matrix4f().rotate(spider.orientation)
    group[spider] = renderModel(spider, cloak, spider.position, options.bodyPlan.bodyModel, transform, options)


    for ((legIndex, leg) in spider.legs.withIndex()) {
        val positions = leg.ik.nodes
        val rotations = leg.ik.segmentModelRotations

        for (segmentIndex in rotations.indices) {
            val segmentPlan = options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue

            val parent = positions[segmentIndex].position

            val segmentTransform = Matrix4f().rotate(rotations[segmentIndex])
            group[legIndex to segmentIndex] = renderModel(spider, cloak, parent, segmentPlan.model, segmentTransform, options)
        }
    }

    return group
}

private fun renderModel(
    spider: SpiderBody,
    cloak: Cloak,
    position: Vector,
    model: DisplayModel,
    transformation: Matrix4f,
    options: SpiderOptions,
): RenderGroup {
    val group = RenderGroup()

    for ((index, piece) in model.pieces.withIndex()) {
        group[index] = renderModelPiece(spider, cloak, position, piece, transformation, options)
    }

    return group
}


private fun renderModelPiece(
    spider: SpiderBody,
    cloak: Cloak,
    position: Vector,
    piece: BlockDisplayModelPiece,
    transformation: Matrix4f,
    options: SpiderOptions,
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

            cloak.getPiece(piece, spider.world, piecePosition, piece.block, piece.brightness, options.cloak)
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