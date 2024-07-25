package com.heledron.spideranimation

object KVElements {
    fun set(obj: Any, key: String, value: Any?) {
        obj::class.java.declaredFields.forEach { field ->
            if (field.name != key) return@forEach

            if (!field.isAnnotationPresent(KVElement::class.java)) return@forEach

            try {
                field.isAccessible = true
                field.set(obj, value)
            } catch (_: Exception) {}
        }
    }

    fun copyMap(obj: Any, map: Map<String, Any?>) {
        map.forEach { (key, value) ->
            set(obj, key, value)
        }
    }

    fun get(obj: Any, key: String): Any? {
        return obj::class.java.declaredFields.mapNotNull { field ->
            if (field.name != key) return@mapNotNull null
            if (!field.isAnnotationPresent(KVElement::class.java)) return@mapNotNull null

            field.isAccessible = true
            field.get(obj)
        }.firstOrNull()
    }

    fun toMap(obj: Any): Map<String, Any?> {
        return obj::class.java.declaredFields.mapNotNull { field ->
            if (!field.isAnnotationPresent(KVElement::class.java)) return@mapNotNull null

            field.isAccessible = true
            field.name to field.get(obj)
        }.toMap()
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class KVElement