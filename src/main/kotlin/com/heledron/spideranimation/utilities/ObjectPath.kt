package com.heledron.spideranimation.utilities

import com.google.gson.Gson
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.util.concurrent.ConcurrentHashMap

class ObjectPath(private val gson: Gson = Serializer.gson) {
    fun get(root: Any, path: String): Any? = walk(root, parse(path))?.value

    fun set(root: Any, path: String, value: Any?): Boolean {
        val keys = parse(path)
        if (keys.isEmpty()) return false

        val parent = walk(root, keys.dropLast(1)) ?: return false
        return write(parent, keys.last(), value)
    }

    fun setAll(root: Any, values: Map<*, *>): Boolean {
        var allWritten = true
        for ((key, value) in values) {
            if (!set(root, key.toString(), value)) allWritten = false
        }
        return allWritten
    }

    fun paths(root: Any, maxDepth: Int = Int.MAX_VALUE): List<String> {
        val paths = mutableListOf<String>()
        collectPaths(gson.fromJson(gson.toJson(root), Any::class.java), "", 0, maxDepth, paths)
        return paths
    }

    fun completions(root: Any, typed: String): List<String> {
        val hops = parse(typed).size
        val startsNewLevel = typed.isNotEmpty() && (typed.last() == '.' || typed.last() == '[')

        val level = when {
            typed.isEmpty() -> 1
            startsNewLevel -> hops + 1
            else -> hops
        }

        // Walk one level deeper than we may return, so a complete path can reveal its children in
        // the same pass rather than walking the tree twice.
        val candidates = paths(root, level + 1).filter { it.startsWith(typed, ignoreCase = true) }
        val isCompletePath = !startsNewLevel && candidates.any { it.equals(typed, ignoreCase = true) }
        val depth = if (isCompletePath) level + 1 else level

        return candidates.filter { parse(it).size <= depth }
    }

    private class Node(val value: Any, val type: Type)

    private fun walk(root: Any, keys: List<String>): Node? {
        var node = Node(root, root.javaClass)
        for (key in keys) node = child(node, key) ?: return null
        return node
    }

    private fun child(parent: Node, key: String): Node? {
        val container = parent.value
        if (container is Map<*, *>) {
            val value = container[key] ?: return null
            return Node(value, mapValueType(parent.type))
        }
        if (container is List<*>) {
            val value = key.toIntOrNull()?.let(container::getOrNull) ?: return null
            return Node(value, elementType(parent.type))
        }

        val field = fieldOf(container.javaClass, key) ?: return null
        val value = read(field, container) ?: return null
        return Node(value, resolve(parent.type, field.genericType))
    }

    private fun write(parent: Node, key: String, value: Any?): Boolean {
        val container = parent.value
        return try {
            when (container) {
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    (container as MutableMap<Any?, Any?>)[key] = convert(value, mapValueType(parent.type))
                    true
                }
                is MutableList<*> -> writeIndex(container, key, value, elementType(parent.type))
                else -> writeField(container, key, value, parent.type)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun writeIndex(list: MutableList<*>, key: String, value: Any?, type: Type): Boolean {
        val index = key.toIntOrNull() ?: return false

        @Suppress("UNCHECKED_CAST")
        val target = list as MutableList<Any?>
        val converted = convert(value, type)

        return when {
            // appending is allowed, e.g. `legs[3]` on a 3 element list
            index == target.size -> { target.add(converted); true }
            index in target.indices -> { target[index] = converted; true }
            else -> false
        }
    }

    private fun writeField(container: Any, key: String, value: Any?, context: Type): Boolean {
        val field = fieldOf(container.javaClass, key) ?: return false
        val converted = convert(value, resolve(context, field.genericType))

        return try {
            field.isAccessible = true
            field.set(container, converted)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun convert(value: Any?, type: Type): Any? = gson.fromJson(gson.toJsonTree(value), type)

    private fun elementType(context: Type): Type =
        (context as? ParameterizedType)?.actualTypeArguments?.getOrNull(0) ?: Any::class.java

    private fun mapValueType(context: Type): Type =
        (context as? ParameterizedType)?.actualTypeArguments?.getOrNull(1) ?: Any::class.java

    private fun resolve(context: Type, declared: Type): Type {
        if (declared !is TypeVariable<*>) return declared

        val parameterized = context as? ParameterizedType ?: return declared
        val owner = parameterized.rawType as? Class<*> ?: return declared
        val index = owner.typeParameters.indexOfFirst { it.genericDeclaration == declared.genericDeclaration }
        return parameterized.actualTypeArguments.getOrNull(index) ?: declared
    }

    private fun read(field: Field, target: Any): Any? = try {
        field.isAccessible = true
        field.get(target)
    } catch (_: Exception) {
        null
    }

    private fun collectPaths(value: Any?, prefix: String, depth: Int, maxDepth: Int, out: MutableList<String>) {
        if (depth >= maxDepth) return

        when (value) {
            is Map<*, *> -> for ((key, child) in value) {
                val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                out += path
                collectPaths(child, path, depth + 1, maxDepth, out)
            }
            is List<*> -> for ((index, child) in value.withIndex()) {
                val path = "$prefix[$index]"
                out += path
                collectPaths(child, path, depth + 1, maxDepth, out)
            }
        }
    }

    private fun fieldOf(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            val field = fields.getOrPut(current) { current.declaredFields.associateBy { it.name } }[name]
            if (field != null) return field
            current = current.superclass
        }
        return null
    }

    private val fields = ConcurrentHashMap<Class<*>, Map<String, Field>>()

    companion object {
        /** Splits `a.b[1].c` into `[a, b, 1, c]`. */
        fun parse(path: String): List<String> =
            path.split('.', '[', ']').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
