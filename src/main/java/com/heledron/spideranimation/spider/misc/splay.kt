package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.events.interval
import com.heledron.spideranimation.utilities.events.runLater
import com.heledron.spideranimation.utilities.maths.eased
import com.heledron.spideranimation.utilities.maths.moveTowards
import com.heledron.spideranimation.utilities.rendering.RenderEntityTracker
import org.bukkit.entity.BlockDisplay
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
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
    for ((id, entity) in RenderEntityTracker.getAll()) {
        if (entity !is BlockDisplay) continue
        entities += entity
        RenderEntityTracker.detach(id)
    }

    onPluginShutdown {
        for (entity in entities) entity.remove()
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
        val offset = entity.location.toVector().subtract(spider.position).toVector3f()
        runLater(3L + i / 4) {
            splay(entity, offset)
        }
    }
}

private fun splay(entity: BlockDisplay, offset: Vector3f) {
    val start = entity.transformation

    val end = entity.transformation
    end.translation.apply {
        this
        .add(offset)
        .normalize()
        .mul(Random.nextDouble(1.0, 3.0).toFloat())
        .sub(offset)
    }
    end.scale.set(.35f)
    end.leftRotation.identity()
    end.rightRotation.identity()

    entity.interpolationDuration = 1
    entity.interpolationDelay = 0

    var t = .0f
    interval(0, 1) {
        t = t.moveTowards(1f, .07f)

        entity.transformation = start.lerp(end, t.eased())
        entity.interpolationDelay = 0

        if (t >= 1) it.close()
    }
}