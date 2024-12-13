package com.heledron.spideranimation.spider.rendering

import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.joml.Matrix4f

class BlockDisplayModelPiece (
    var block: BlockData,
    var transform: Matrix4f,
    var brightness: Display.Brightness? = null,
    var tags: List<String> = emptyList(),
) {
    fun scale(scale: Float) {
        transform.set(Matrix4f().scale(scale).mul(transform))
    }

    fun scale(x: Float, y: Float, z: Float) {
        transform.set(Matrix4f().scale(x, y, z).mul(transform))
    }

    fun clone() = BlockDisplayModelPiece(
        block = block.clone(),
        transform = Matrix4f(transform),
        brightness = brightness?.let { Display.Brightness(it.blockLight, it.skyLight) },
        tags = tags,
    )
}

class DisplayModel(var pieces: List<BlockDisplayModelPiece>) {
    fun scale(scale: Float) = apply {
        pieces.forEach { it.scale(scale, scale, scale) }
    }

    fun scale(x: Float, y: Float, z: Float) = apply {
        pieces.forEach { it.scale(x, y, z) }
    }

    fun clone() = DisplayModel(pieces.map { it.clone() })
}