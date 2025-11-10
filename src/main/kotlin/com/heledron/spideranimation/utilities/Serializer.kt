package com.heledron.spideranimation.utilities

import com.google.gson.Gson

object Serializer {
    val gson = Gson()

    fun toNullableMap(obj: Any?): Any? {
        if (obj == null) return null
        return toMap(obj)
    }

    fun toMap(obj: Any): Any {
        return gson.fromJson(gson.toJson(obj), Any::class.java)
    }

    fun <T> fromMap(map: Any, clazz: Class<T>): T {
        return gson.fromJson(gson.toJson(map), clazz)
    }

    fun writeFromMap(obj: Any, map: Map<*, *>) {
        for ((key, value) in map) {
            setShallow(obj, key.toString(), value)
        }
    }

    fun get(obj: Any, path: String): Any? {
        return get(obj, parsePath(path))
    }

    private fun get(obj: Any, path: List<String>): Any? {
        var current: Any? = obj
        for (key in path) current = getShallow(current ?: return null, key)
        return current
    }

    fun setMap(obj: Any, path: String, map: Any?) {
        val pathList = parsePath(path)
        val newObj = withSetMap(obj, pathList, map)
        set(obj, path, get(newObj, path))
    }

    fun set(obj: Any, path: String, value: Any?) {
        val pathList = parsePath(path)
        val parent = get(obj, pathList.dropLast(1)) ?: return
        setShallow(parent, pathList.last(), value)
    }

    private fun parsePath(path: String): List<String> {
        return path.split("[.\\[\\]]".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun<T : Any> withSetMap(obj: T, path: List<String>, value: Any?): T {
        val map = toMap(obj)
        val mapParent = get(map, path.dropLast(1)) ?: return obj
        setShallow(mapParent, path.last(), value)
        return fromMap(map, obj.javaClass)
    }

    private fun getShallow(current: Any, key: String): Any? {
        if (current is Map<*, *>) return current[key]

        if (current is List<*>) return current[key.toInt()]

        return try {
            val field = current.javaClass.getDeclaredField(key)
            field.isAccessible = true
            field.get(current)
        } catch (e: Exception) {
            null
        }
    }

    private fun setShallow(current: Any, key: String, value: Any?) {
        try {
            if (current is MutableMap<*, *>) {
                (current as MutableMap<String, Any?>)[key] = value
                return
            }

            if (current is MutableList<*>) {
                val index = key.toInt()
                if (index == current.size) (current as MutableList<Any?>).add(value)
                else (current as MutableList<Any?>)[index] = value
                return
            }

            val field = current.javaClass.getDeclaredField(key)
            field.isAccessible = true
            field.set(current, value)
        } catch (_: Exception) { }
    }
}