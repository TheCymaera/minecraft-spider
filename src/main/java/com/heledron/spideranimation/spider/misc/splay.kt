package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.utilities.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.Closeable
import kotlin.random.Random

private fun Transformation.lerp(newTransform: Transformation, lerpAmount: Float): Transformation {

    this.translation.lerp(newTransform.translation, lerpAmount)
    this.scale.lerp(newTransform.scale, lerpAmount)
    this.leftRotation.slerp(newTransform.leftRotation, lerpAmount)
    this.rightRotation.slerp(newTransform.rightRotation, lerpAmount)

    return this
}

fun Transformation.clone() = Transformation(
    Vector3f(translation),
    Quaternionf(leftRotation),
    Vector3f(scale),
    Quaternionf(rightRotation)
)


fun splay() {
    val spider = AppState.spider ?: return

    // detach and get entities
    val entities = mutableListOf<BlockDisplay>()
    for ((id, entity) in AppState.renderer.rendered.toList()) {
        if (entity !is BlockDisplay) continue
        entities += entity
        AppState.renderer.detach(id)
        AppState.closeables += Closeable { entity.remove() }
    }

    AppState.spider = null


    val pieces = mutableListOf<BlockDisplayModelPiece>()
    for (piece in spider.options.bodyPlan.bodyModel.pieces) {
        pieces += piece
    }

    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        for ((segmentIndex, segment) in leg.chain.segments.withIndex()) {
            val model = spider.options.bodyPlan.legs[legIndex].segments[segmentIndex].model
            for (piece in model.pieces) pieces += piece
        }
    }

    for ((i, entity) in entities.withIndex().shuffled()) {
        val offset = entity.location.toVector().subtract(spider.position)

        // normalize position
        entity.teleportDuration = 0
        entity.interpolationDuration = 0
        entity.interpolationDelay = 100

        val transformation = entity.transformation
        runLater(2) {
            entity.teleport(spider.position.toLocation(spider.world))

            transformation.translation.add(offset.toVector3f())
            entity.transformation = transformation
        }

        runLater(3L + i / 4) {
            splay(entity)
        }
    }
}

private fun splay(entity: BlockDisplay) {
    val targetTransformation = entity.transformation
    targetTransformation.translation.apply {
        normalize().mul(Random.nextDouble(1.0, 3.0).toFloat())
    }
    targetTransformation.scale.set(.35f)
    targetTransformation.leftRotation.identity()
    targetTransformation.rightRotation.identity()

    entity.interpolationDuration = 1
    entity.interpolationDelay = 0

    var lerpAmount = .0f
    interval(0, 1) {
        lerpAmount = lerpAmount.moveTowards(1f, .1f)

        val eased = lerpAmount * lerpAmount * (3 - 2 * lerpAmount)
        entity.transformation = entity.transformation.lerp(targetTransformation, eased)
        entity.interpolationDelay = 0

        if (lerpAmount >= 1) it.close()
    }
}