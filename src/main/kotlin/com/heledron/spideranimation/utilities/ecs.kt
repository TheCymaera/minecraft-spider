package com.heledron.spideranimation.utilities

/**
 * Bevy-style Entity Component System
 */
class ECS {
    var entities = mutableListOf<ECSEntity>()
    val eventListeners = mutableListOf<(Any) -> Unit>()

    private val startSystems = mutableListOf<(ECS) -> Unit>()
    private val tickSystems = mutableListOf<(ECS) -> Unit>()
    private val renderSystems = mutableListOf<(ECS) -> Unit>()

    fun onStart(func: (ECS) -> Unit) { startSystems += func }
    fun onTick(func: (ECS) -> Unit) { tickSystems += func }
    fun onRender(func: (ECS) -> Unit) { renderSystems += func }

    inline fun<reified T: Any> onEvent(listener: (T) -> Unit) {
        eventListeners += { event ->
            if (event is T) listener(event)
        }
    }

    fun <T : Any>emit(message: T) {
        for (listener in eventListeners) listener(message)
    }

    fun spawn(vararg components: Any): ECSEntity {
        val entity = ECSEntity()
        for (component in components) {
            entity.addComponent(component)
        }
        entities.add(entity)
        return entity
    }

    @JvmName("query1")
    inline fun <reified T : Any> query(): Iterable<T> {
//        if (!inSystem) throw Error("Cannot query outside of a system")
        return entities.mapNotNull { it.query<T>()  }
    }

    @JvmName("query2")
    inline fun <reified A : Any, reified B : Any> query(): Iterable<Pair<A, B>> {
//        if (!inSystem) throw Error("Cannot query outside of a system")
       return entities.mapNotNull { entity ->
            val comp1 = entity.query<A>() ?: return@mapNotNull null
            val comp2 = entity.query<B>() ?: return@mapNotNull null
            Pair(comp1, comp2)
        }
    }

    @JvmName("query3")
    inline fun <reified A : Any, reified B : Any, reified C : Any> query(): Iterable<Triple<A, B, C>> {
//        if (!inSystem) throw Error("Cannot query outside of a system")
       return entities.mapNotNull { entity ->
            val comp1 = entity.query<A>() ?: return@mapNotNull null
            val comp2 = entity.query<B>() ?: return@mapNotNull null
            val comp3 = entity.query<C>() ?: return@mapNotNull null
            Triple(comp1, comp2, comp3)
        }
    }

    @JvmName("query4")
    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> query(): Iterable<Quadruple<A, B, C, D>> {
//        if (!inSystem) throw Error("Cannot query outside of a system")
        return entities.mapNotNull { entity ->
            val comp1 = entity.query<A>() ?: return@mapNotNull null
            val comp2 = entity.query<B>() ?: return@mapNotNull null
            val comp3 = entity.query<C>() ?: return@mapNotNull null
            val comp4 = entity.query<D>() ?: return@mapNotNull null
            Quadruple(comp1, comp2, comp3, comp4)
        }
    }

    fun start() {
        for (system in startSystems) system(this)
    }

    fun update() {
        for (system in tickSystems) system(this)

        entities.removeIf { it.scheduledForRemoval }
    }

    fun render() {
        for (system in renderSystems) system(this)
    }
}


class ECSEntity {
    val components = mutableListOf<Any>()

    var scheduledForRemoval = false

    fun remove() {
        this.scheduledForRemoval = true
    }

    inline fun <reified T : Any> addComponent(component: T) {
        components.add(component)
    }

    inline fun <reified  T: Any> removeComponent() {
        components.removeIf { it is T }
    }

    inline fun <reified Old: Any> replaceComponent(component: Any) {
        removeComponent<Old>()
        addComponent(component)
    }

    inline fun <reified T : Any> query(): T? {
        if (this is T) return this
        return components.find { it is T } as T?
    }
}


class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val forth: D,
) {
    operator fun component1() = first
    operator fun component2() = second
    operator fun component3() = third
    operator fun component4() = forth
}