package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.utilities.rendering.interpolateTransform
import com.heledron.spideranimation.utilities.rendering.renderBlock
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.util.Vector
import org.joml.Matrix4f

//import com.heledron.spideranimation.utilities.deprecated.applyTransformationWithInterpolation
//import com.heledron.spideranimation.utilities.deprecated.spawnEntity
//import org.bukkit.Location
//import org.bukkit.World
//import org.bukkit.entity.BlockDisplay
//import org.bukkit.entity.Display
//import org.bukkit.entity.Entity
//import org.bukkit.entity.TextDisplay
//import org.bukkit.util.Vector
//import org.joml.Matrix4f
//import java.io.Closeable
//
//class RenderEntity <T : Entity> (
//    val clazz : Class<T>,
//    val location : Location,
//    val init : (T) -> Unit = {},
//    val update : (T) -> Unit = {}
//)
//
//class RenderEntityGroup {
//    val items = mutableMapOf<Any, RenderEntity<out Entity>>()
//
//    fun add(id: Any, item: RenderEntity<out Entity>) {
//        items[id] = item
//    }
//
//    fun add(id: Any, item: RenderEntityGroup) {
//        for ((subId, part) in item.items) {
//            items[id to subId] = part
//        }
//    }
//}
//
//fun blockRenderEntity(
//    location: Location,
//    init: (BlockDisplay) -> Unit = {},
//    update: (BlockDisplay) -> Unit = {}
//) = RenderEntity(
//    clazz = BlockDisplay::class.java,
//    location = location,
//    init = init,
//    update = update
//)
//
//fun blockRenderEntity(
//    world: World,
//    position: Vector,
//    init: (BlockDisplay) -> Unit = {},
//    update: (BlockDisplay) -> Unit = {}
//) = RenderEntity(
//    clazz = BlockDisplay::class.java,
//    location = position.toLocation(world),
//    init = init,
//    update = update
//)
//
fun renderLine(
    world: World,
    position: Vector,
    vector: Vector,
    upVector: Vector = if (vector.x + vector.z != 0.0) UP_VECTOR else FORWARD_VECTOR,
    thickness: Float = .1f,
    interpolation: Int = 1,
    init: (BlockDisplay) -> Unit = {},
    update: (BlockDisplay) -> Unit = {}
) = renderBlock(
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

        it.interpolateTransform(matrix)
        update(it)
    }
)
//
//fun textRenderEntity(
//    location: Location,
//    text: String,
//    interpolation: Int,
//    init: (TextDisplay) -> Unit = {},
//    update: (TextDisplay) -> Unit = {},
//) = RenderEntity(
//    clazz = TextDisplay::class.java,
//    location = location,
//    init = {
//        it.teleportDuration = interpolation
//        it.billboard = Display.Billboard.CENTER
//        init(it)
//    },
//    update = {
//        it.text = text
//        update(it)
//    }
//)
//
//fun textRenderEntity(
//    world: World,
//    position: Vector,
//    text: String,
//    interpolation: Int,
//    init: (TextDisplay) -> Unit = {},
//    update: (TextDisplay) -> Unit = {},
//) = textRenderEntity(
//    location = position.toLocation(world),
//    text = text,
//    interpolation = interpolation,
//    init = init,
//    update = update
//)
//
//class SingleEntityRenderer<T : Entity>: Closeable {
//    var entity: T? = null
//
//    fun render(part: RenderEntity<T>) {
//        entity = (entity ?: spawnEntity(part.location, part.clazz) {
//            part.init(it)
//        }).apply {
//            this.teleport(part.location)
//            part.update(this)
//        }
//    }
//
//    fun renderIf(predicate: Boolean, entity: RenderEntity<T>) {
//        if (predicate) render(entity) else close()
//    }
//
//    override fun close() {
//        entity?.remove()
//        entity = null
//    }
//}
//
//class GroupEntityRenderer: Closeable {
//    val rendered = mutableMapOf<Any, Entity>()
//
//    private val used = mutableSetOf<Any>()
//
//    fun detachEntity(id: Any) {
//        rendered.remove(id)
//    }
//
//    override fun close() {
//        for (entity in rendered.values) {
//            entity.remove()
//        }
//        rendered.clear()
//        used.clear()
//    }
//
//    fun render(group: RenderEntityGroup) {
//        for ((id, template) in group.items) {
//            renderPart(id, template)
//        }
//
//        val toRemove = rendered.keys - used
//        for (key in toRemove) {
//            val entity = rendered[key]!!
//            entity.remove()
//            rendered.remove(key)
//        }
//        used.clear()
//    }
//
//    fun <T: Entity>render(part: RenderEntity<T>) {
//        val group = RenderEntityGroup().apply { add(0, part) }
//        render(group)
//    }
//
//    private fun <T: Entity>renderPart(id: Any, template: RenderEntity<T>) {
//        used.add(id)
//
//        val oldEntity = rendered[id]
//        if (oldEntity != null) {
//            // check if the entity is of the same type
//            if (oldEntity.type.entityClass == template.clazz) {
//                oldEntity.teleport(template.location)
//                @Suppress("UNCHECKED_CAST")
//                template.update(oldEntity as T)
//                return
//            }
//
//            oldEntity.remove()
//            rendered.remove(id)
//        }
//
//        val entity = spawnEntity(template.location, template.clazz) {
//            template.init(it)
//            template.update(it)
//        }
//        rendered[id] = entity
//    }
//}
//
//
//class MultiEntityRenderer: Closeable {
//    private val renderer = GroupEntityRenderer()
//    private var group = RenderEntityGroup()
//
//    fun render(id: Any, group: RenderEntityGroup) {
//        this.group.add(id, group)
//    }
//
//    fun render(id: Any, entity: RenderEntity<out Entity>) {
//        this.group.add(id, entity)
//    }
//
//    fun flush() {
//        renderer.render(group)
//        group = RenderEntityGroup()
//    }
//
//    val rendered: Map<Any, Entity> get() = renderer.rendered
//
//    fun detach(id: Any) {
//        renderer.detachEntity(id)
//    }
//
//    override fun close() {
//        renderer.close()
//    }
//}