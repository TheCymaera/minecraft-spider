@file:Suppress("UNCHECKED_CAST")

package com.heledron.spideranimation.utilities.rendering

import com.heledron.spideranimation.utilities.custom_entities.CustomEntityComponent
import com.heledron.spideranimation.utilities.custom_entities.attach
import com.heledron.spideranimation.utilities.custom_entities.detach
import com.heledron.spideranimation.utilities.events.onTickEnd
import com.heledron.spideranimation.utilities.onPluginShutdown
import org.bukkit.Location
import org.bukkit.entity.Entity
import kotlin.collections.iterator

interface RenderItem {
    fun submit(handle: Any)
}

object EmptyRenderItem : RenderItem {
    override fun submit(handle: Any) {
        // No operation
    }
}

class RenderGroup: RenderItem {
    private val children = mutableMapOf<Any, RenderItem>()

//    fun add(handle: Any, item: RenderItem) {
//        children[handle] = item
//    }

    operator fun set(handle: Any, item: RenderItem) {
        children[handle] = item
    }

    operator fun get(handle: Any): RenderItem? {
        return children[handle]
    }

    override fun submit(handle: Any) {
        for ((childHandle, item) in children) {
            item.submit(handle to childHandle)
        }
    }
}

class RenderEntity <T : Entity> (
    val clazz : Class<T>,
    val location : Location,
    val init : (T) -> Unit = {},
    val update : (T) -> Unit = {},
): RenderItem {
    override fun submit(handle: Any) {
        val entity = RenderEntityTracker.get(handle, clazz)

        if (entity != null) {
            if (!entity.isInsideVehicle) entity.teleport(location)
            update(entity)
        } else {
            RenderEntityTracker.put(handle, location.world!!.spawn(location, clazz) {
                init(it)
                update(it)
            })
        }
    }
}

object RenderEntityTracker {
    private val rendered = mutableMapOf<Any, Entity>()
    private val used = mutableSetOf<Any>()

    private val component = CustomEntityComponent.fromString("RenderEntity")

    init {
        onTickEnd {
            removeUnused()
        }

        onPluginShutdown {
            removeAll()
        }

        // remove dangling entities from previous crashes / failed shutdowns
        removeAllByTag()
    }

    fun detach(id: Any) {
        rendered[id]?.detach(component)
        rendered.remove(id)
        used.remove(id)
    }

    fun <T : Entity>get(handle: Any, clazz: Class<T>): T? {
        val existing = rendered[handle]
        val isValid = existing != null && existing.type.entityClass == clazz && existing.isValid
        if (!isValid) {
            remove(handle)
            return null
        }
        used.add(handle)
        return existing as T
    }

    fun getAll(): List<Pair<Any, Entity>> {
        return rendered.toList()
    }

    fun <T : Entity>put(handle: Any, entity: T): T {
        @Suppress("UNCHECKED_CAST")
        rendered.putIfAbsent(handle, entity)
        entity.attach(component)
        used.add(handle)
        return entity
    }

    fun remove(handle: Any) {
        val entity = rendered[handle] ?: return
        entity.remove()
        rendered.remove(handle)
    }

    private fun removeUnused() {
        val toRemove = rendered.keys - used
        for (key in toRemove) {
            val entity = rendered[key]!!
            entity.remove()
            rendered.remove(key)
        }
        used.clear()
    }

    fun removeAll() {
        for (entity in rendered.values) {
            if (!component.isAttached(entity)) continue
            entity.remove()
        }
        rendered.clear()
    }

    fun removeAllByTag() {
        component.entities().forEach {
            it.remove()
        }
    }
}