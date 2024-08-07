package com.heledron.spideranimation

import com.google.gson.Gson

object ObjectMapper {
    val gson = Gson()

    fun toMap(obj: Any?): Map<*, *> {
        return gson.fromJson(gson.toJson(obj), Map::class.java)
    }

    fun <T> fromMap(map: Map<*, *>, clazz: Class<T>): T {
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
        for (key in path) current = getShallow(current, key)
        return current
    }

    fun setJSON(obj: Any, path: String, jsonString: String) {
        val pathList = parsePath(path)
        val newObj = withSetJSON(obj, pathList, jsonString)
        set(obj, path, get(newObj, path))
    }

    fun set(obj: Any, path: String, value: Any?) {
        val pathList = parsePath(path)
        val parent = get(obj, pathList.dropLast(1))
        setShallow(parent, pathList.last(), value)
    }

    private fun<T : Any> withSetJSON(obj: T, path: List<String>, jsonString: String): T {
        val map = toMap(obj)
        val newValue = gson.fromJson(jsonString, Any::class.java)
        setShallow(get(map, path.dropLast(1)), path.last(), newValue)

        return fromMap(map, obj.javaClass)
    }

    private fun getShallow(current: Any?, key: String): Any? {
        if (current == null) return null

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

    private fun setShallow(current: Any?, key: String, value: Any?) {
        try {
            if (current == null) return

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

    private fun parsePath(path: String): List<String> {
        return path.split("[.\\[\\]]".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
    }
}