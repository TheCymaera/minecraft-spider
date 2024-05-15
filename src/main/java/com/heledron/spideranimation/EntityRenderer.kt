package com.heledron.spideranimation

import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import org.joml.Matrix4f
import java.io.Closeable

class EntityRendererTemplate <T : Entity> (
    val clazz : Class<T>,
    val location : Location,
    val init : (T) -> Unit = {},
    val update : (T) -> Unit = {}
)

fun blockTemplate(
    location: Location,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = EntityRendererTemplate(
    clazz = BlockDisplay::class.java,
    location = location,
    init = init,
    update = update
)

fun targetTemplate(
    location: Location
) = blockTemplate(
    location = location,
    init = {
        it.block = org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = org.bukkit.entity.Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

fun lineTemplate(
    location: Location,
    vector: Vector,
    upVector: Vector = if (vector.x + vector.z != 0.0) UP_VECTOR else Vector(0, 0, 1),
    thickness: Float = .1f,
    interpolation: Int = 1,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = blockTemplate(
    location = location,
    init = {
        it.teleportDuration = interpolation
        it.interpolationDuration = interpolation
        init(it)
    },
    update = {
        val matrix = Matrix4f().rotateTowards(vector.toVector3f(), upVector.toVector3f())
            .translate(-thickness / 2, -thickness / 2, 0f)
            .scale(thickness, thickness, vector.length().toFloat())

        applyTransformationWithInterpolation(it, matrix)
        update(it)
    }
)

class EntityRenderer<T : Entity>: Closeable {
    var entity: T? = null

    fun render(template: EntityRendererTemplate<T>) {
        entity = (entity ?: spawnEntity(template.location, template.clazz) {
            template.init(it)
        }).apply {
            this.teleport(template.location)
            template.update(this)
        }
    }

    fun renderIf(predicate: Boolean, template: EntityRendererTemplate<T>) {
        if (predicate) render(template) else close()
    }

    override fun close() {
        entity?.remove()
        entity = null
    }
}

class MultiEntityRenderer: Closeable {
    val rendered = mutableMapOf<Any, Entity>()

    val used = mutableSetOf<Any>()

    override fun close() {
        for (entity in rendered.values) {
            entity.remove()
        }
        rendered.clear()
        used.clear()
    }

    fun beginRender() {
        if (used.isNotEmpty()) {
            throw IllegalStateException("beginRender called without finishRender")
        }
    }

    fun keepAlive(id: Any) {
        used.add(id)
    }

    fun finishRender() {
        val toRemove = rendered.keys - used
        for (key in toRemove) {
            val entity = rendered[key]!!
            entity.remove()
            rendered.remove(key)
        }
        used.clear()
    }

    fun <T: Entity>render(id: Any, template: EntityRendererTemplate<T>) {
        used.add(id)

        val oldEntity = rendered[id]
        if (oldEntity != null) {
            // check if the entity is of the same type
            if (oldEntity.type.entityClass == template.clazz) {
                oldEntity.teleport(template.location)
                @Suppress("UNCHECKED_CAST")
                template.update(oldEntity as T)
                return
            }

            oldEntity.remove()
            rendered.remove(id)
        }

        val entity = spawnEntity(template.location, template.clazz) {
            template.init(it)
            template.update(it)
        }
        rendered[id] = entity
    }

    fun renderList(id: Any, list: List<EntityRendererTemplate<*>>) {
        for ((i, template) in list.withIndex()) {
            render(id to i, template)
        }
    }
}


