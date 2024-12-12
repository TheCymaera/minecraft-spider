package com.heledron.spideranimation.utilities

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import org.joml.Matrix4f
import java.io.Closeable

class ModelPart <T : Entity> (
    val clazz : Class<T>,
    val location : Location,
    val init : (T) -> Unit = {},
    val update : (T) -> Unit = {}
)

class Model {
    val parts = mutableMapOf<Any, ModelPart<out Entity>>()

    fun add(id: Any, part: ModelPart<out Entity>) {
        parts[id] = part
    }

    fun add(id: Any, model: Model) {
        for ((subId, part) in model.parts) {
            parts[id to subId] = part
        }
    }
}

fun blockModel(
    location: Location,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = ModelPart(
    clazz = BlockDisplay::class.java,
    location = location,
    init = init,
    update = update
)

fun blockModel(
    world: World,
    position: Vector,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = ModelPart(
    clazz = BlockDisplay::class.java,
    location = position.toLocation(world),
    init = init,
    update = update
)

fun lineModel(
    world: World,
    position: Vector,
    vector: Vector,
    upVector: Vector = if (vector.x + vector.z != 0.0) UP_VECTOR else FORWARD_VECTOR,
    thickness: Float = .1f,
    interpolation: Int = 1,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = blockModel(
    world = world,
    position = position,
    init = {
        it.teleportDuration = interpolation
        it.interpolationDuration = interpolation
        init(it)
    },
    update = {
        val matrix = Matrix4f().rotateTowards(vector.toVector3f(), upVector.toVector3f())
            .translate(-thickness / 2, -thickness / 2, 0f)
            .scale(thickness, thickness, vector.length().toFloat())

        it.applyTransformationWithInterpolation(matrix)
        update(it)
    }
)

fun textModel(
    location: Location,
    text: String,
    interpolation: Int,
    init: (TextDisplay) -> Unit = {},
    update: (TextDisplay) -> Unit = {},
) = ModelPart(
    clazz = TextDisplay::class.java,
    location = location,
    init = {
        it.teleportDuration = interpolation
        it.billboard = Display.Billboard.CENTER
        init(it)
    },
    update = {
        it.text = text
        update(it)
    }
)

fun textModel(
    world: World,
    position: Vector,
    text: String,
    interpolation: Int,
    init: (TextDisplay) -> Unit = {},
    update: (TextDisplay) -> Unit = {},
) = textModel(
    location = position.toLocation(world),
    text = text,
    interpolation = interpolation,
    init = init,
    update = update
)

class ModelPartRenderer<T : Entity>: Closeable {
    var entity: T? = null

    fun render(part: ModelPart<T>) {
        entity = (entity ?: spawnEntity(part.location, part.clazz) {
            part.init(it)
        }).apply {
            this.teleport(part.location)
            part.update(this)
        }
    }

    fun renderIf(predicate: Boolean, template: ModelPart<T>) {
        if (predicate) render(template) else close()
    }

    override fun close() {
        entity?.remove()
        entity = null
    }
}

class ModelRenderer: Closeable {
    val rendered = mutableMapOf<Any, Entity>()

    private val used = mutableSetOf<Any>()

    override fun close() {
        for (entity in rendered.values) {
            entity.remove()
        }
        rendered.clear()
        used.clear()
    }

    fun render(model: Model) {
        for ((id, template) in model.parts) {
            renderPart(id, template)
        }

        val toRemove = rendered.keys - used
        for (key in toRemove) {
            val entity = rendered[key]!!
            entity.remove()
            rendered.remove(key)
        }
        used.clear()
    }

    fun <T: Entity>render(part: ModelPart<T>) {
        val model = Model().apply { add(0, part) }
        render(model)
    }

    private fun <T: Entity>renderPart(id: Any, template: ModelPart<T>) {
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
}


class MultiModelRenderer: Closeable {
    private val renderer = ModelRenderer()
    private var model = Model()

    fun render(id: Any, model: Model) {
        this.model.add(id, model)
    }

    fun render(id: Any, model: ModelPart<out Entity>) {
        this.model.add(id, model)
    }

    fun flush() {
        renderer.render(model)
        model = Model()
    }

    override fun close() {
        renderer.close()
    }
}