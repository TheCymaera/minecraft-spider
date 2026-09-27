package com.heledron.spideranimation.spider.configuration

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.heledron.spideranimation.utilities.inverse_kinematics.IKHingeJoint3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKJoint3D
import com.heledron.spideranimation.utilities.inverse_kinematics.IKSwingTwistJoint3D
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.joml.Matrix4f
import java.lang.reflect.Type

object SpiderOptionsSerializer {
    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(BlockData::class.java, BlockDataAdapter)
        .registerTypeHierarchyAdapter(Sound::class.java, SoundAdapter)
        .registerTypeAdapter(IKJoint3D::class.java, IKJointAdapter)
        .registerTypeAdapter(Matrix4f::class.java, Matrix4fAdapter)
        .create()

    fun serialize(options: SpiderOptions): String = gson.toJson(options)

    fun deserialize(json: String): SpiderOptions {
        val options = gson.fromJson(json, SpiderOptions::class.java)
        return options
    }
}

private object BlockDataAdapter : JsonSerializer<BlockData>, JsonDeserializer<BlockData> {
    override fun serialize(src: BlockData, type: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.asString)
    }

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): BlockData {
        return Bukkit.createBlockData(json.asString)
    }
}

private object Matrix4fAdapter : JsonSerializer<Matrix4f>, JsonDeserializer<Matrix4f> {
    override fun serialize(src: Matrix4f, type: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonArray().apply {
            for (value in src.get(FloatArray(16))) add(value)
        }
    }

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Matrix4f {
        val array = json.asJsonArray
        val values = FloatArray(16) { array[it].asFloat }
        return Matrix4f().set(values)
    }
}

private object SoundAdapter : JsonSerializer<Sound>, JsonDeserializer<Sound> {
    override fun serialize(src: Sound, type: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonPrimitive(Registry.SOUNDS.getKey(src).toString())
    }

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Sound {
        return Registry.SOUNDS.getOrThrow(Key.key(json.asString))
    }
}

private object IKJointAdapter : JsonSerializer<IKJoint3D>, JsonDeserializer<IKJoint3D> {
    private const val KEY = "joint"
    private const val HINGE = "hinge"
    private const val SWING_TWIST = "swingTwist"

    private fun jointTagOf(joint: IKJoint3D): String = when (joint) {
        is IKHingeJoint3D -> HINGE
        is IKSwingTwistJoint3D -> SWING_TWIST
    }

    override fun serialize(src: IKJoint3D, type: Type, ctx: JsonSerializationContext): JsonElement {
        val body = ctx.serialize(src, src.javaClass)
        require(body is JsonObject) { "An IKJoint3D must serialise to a JSON object, got $body" }

        return JsonObject().apply {
            addProperty(KEY, jointTagOf(src))
            for (entry in body.entrySet()) add(entry.key, entry.value)
        }
    }

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): IKJoint3D {
        val body = json.asJsonObject

        val tag = body.get(KEY)?.asString
            ?: throw JsonParseException("IKJoint3D is missing its \"$KEY\" discriminator")

        // Drop the tag so the implementation only sees its own fields
        val fields = JsonObject().apply {
            for (entry in body.entrySet()) if (entry.key != KEY) add(entry.key, entry.value)
        }

        return when (tag) {
            HINGE -> ctx.deserialize(fields, IKHingeJoint3D::class.java)
            SWING_TWIST -> ctx.deserialize(fields, IKSwingTwistJoint3D::class.java)
            else -> throw JsonParseException("Unknown IKJoint3D type \"$tag\"")
        }
    }
}
