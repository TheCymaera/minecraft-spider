package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.misc.runCommandSilently
import com.heledron.spideranimation.utilities.*
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector4f
import kotlin.random.Random

fun targetModel(
    location: Location
) = blockModel(
    location = location,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

fun spiderModel(spider: Spider, bodyModel: List<SpiderModelPiece>): Model {
    val scale = spider.options.bodyPlan.scale

    val model = Model()

    // render body
    for ((index, info) in bodyModel.withIndex()) {
        val id = "body" to index
        model.add(id, blockModel(
            location = spider.position.toLocation(spider.world),
            init = {
                it.teleportDuration = 1
                it.interpolationDuration = 1
                it.brightness = info.brightness
            },
            update = {
                val transform = Matrix4f()
                    .rotate(Quaternionf(spider.orientation))
                    .mul(info.transformation)

                it.applyTransformationWithInterpolation(transform)

                it.block = if (!info.cloak) {
                    info.block
                } else {
                    val relative = transform.transform(Vector4f(.5f, .5f, .5f, 1f))
                    val pieceLocation = spider.position.clone()
                    pieceLocation.x += relative.x
                    pieceLocation.y += relative.y
                    pieceLocation.z += relative.z
                    spider.cloak.getPiece(id, pieceLocation) ?: info.block
                }
            }
        ))
    }


    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        val chain = leg.chain

        // up vector is the cross product of the y-axis and the end-effector direction
        fun segmentUpVector(): Vector {
            val direction = chain.getEndEffector().clone().subtract(chain.root)
            return direction.clone().crossProduct(Vector(0, 1, 0))
        }

        val segmentUpVector = segmentUpVector()

        // Render leg segment

        // get material from namespacedID
        for ((segmentIndex, segment) in chain.segments.withIndex()) {
            val segmentPlan = spider.options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue;

            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root
            val vector = segment.position.clone().subtract(parent).normalize().multiply(segment.length)

            model.add(Pair(legIndex, segmentIndex), lineModel(
                world = spider.world,
                position = parent.clone(),
                vector = vector,
                thickness = segmentPlan.thickness.toFloat(),
                upVector = segmentUpVector,
                update = {
                    val cloak = spider.cloak.getPiece(legIndex to segmentIndex, parent)
                    it.block =  cloak ?: spider.options.bodyPlan.material.createBlockData()
                }
            ))
        }
    }

    return model
}



object BodyModels {
    val FLAT= parseModelFromCommand(
        command = """/summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_deepslate_slab",Properties:{type:"bottom"}},transformation:[0f,0f,0.75f,-0.38f,0f,0.9375f,0f,-0.25f,-0.9375f,0f,0f,0.375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0f,0f,0.625f,-0.3175f,0f,0.5f,0f,-0.1875f,-1.8125f,0f,0f,0.75f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.861f,0f,0f,-0.4356f,0f,0.6875f,0f,-0.5f,0f,0f,0.6875f,-0.6875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.861f,0f,0f,-0.4356f,0f,0.6875f,0f,-0.5f,0f,0f,0.6875f,-0.1875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0f,0f,0.375f,-0.1925f,0f,0.3125f,0f,-0.125f,-0.8125f,0f,0f,1.0556f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_shulker_box",Properties:{}},transformation:[0f,0f,0.125f,-0.0675f,0.125f,0f,0f,-0.0313f,0f,0.125f,0f,0.9375f,0f,0f,0f,1f],brightness:{sky:15,block:15}},{id:"minecraft:block_display",block_state:{Name:"minecraft:black_shulker_box",Properties:{}},transformation:[0f,0f,0.04f,-0.13f,0.04f,0f,0f,-0.0919f,0f,0.125f,0f,0.9375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:black_shulker_box",Properties:{}},transformation:[0f,0f,0.04f,-0.0675f,0.04f,0f,0f,-0.0919f,0f,0.125f,0f,0.9375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:black_shulker_box",Properties:{}},transformation:[0f,0f,0.125f,-0.005f,0.04f,0f,0f,-0.0919f,0f,0.125f,0f,0.9375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0f,0f,0.1f,-0.2675f,-0.0225f,0.0338f,0f,0.405f,-0.6436f,-0.0012f,0f,-0.3375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0f,0f,0.1f,0.1325f,-0.0225f,0.0338f,0f,0.405f,-0.6436f,-0.0012f,0f,-0.3375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_concrete",Properties:{}},transformation:[0f,0f,0.1f,-0.1175f,-0.0041f,0.0099f,0f,0.4008f,-0.117f,-0.0003f,0f,-0.3959f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_concrete",Properties:{}},transformation:[0f,0f,0.05f,-0.0675f,-0.002f,0.0099f,0f,0.3947f,-0.0585f,-0.0003f,0f,-0.5727f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_concrete",Properties:{}},transformation:[0f,0f,0.05f,-0.0675f,-0.002f,0.0099f,0f,0.3905f,-0.0585f,-0.0003f,0f,-0.6908f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:black_shulker_box",Properties:{}},transformation:[0f,0f,0.3f,-0.1675f,-0.0225f,0.0076f,0f,0.405f,-0.6436f,-0.0003f,0f,-0.3375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.8f,-0.4175f,-0.0508f,0.4577f,0f,-0.0463f,-1.4539f,-0.016f,0f,-0.1459f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_stairs",Properties:{facing:"east",half:"bottom",shape:"straight"}},transformation:[-0.6762f,-0.6762f,0f,0.6763f,0f,0f,-0.3491f,0.3242f,1.3206f,-1.3206f,0f,-0.8012f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.625f,-0.3175f,0.0702f,0.1611f,0f,0.1578f,-0.5863f,0.0494f,0f,1.2763f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0.317f,-0.1765f,0f,0.3075f,0f,0f,0.4375f,-0.125f,-0.8823f,-0.0634f,0f,1.2813f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0f,0f,0.1875f,-0.5031f,0.205f,0.2542f,0f,-0.125f,-0.3282f,0.4065f,0f,0.3619f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.5f,-0.505f,0.3019f,0f,0f,0.0789f,0f,0.698f,0f,0.0369f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[-0.3145f,0f,0.1762f,-0.3175f,0f,0.4375f,0f,-0.125f,-0.864f,0f,-0.0641f,1.2813f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0f,0f,0.1874f,0.3075f,0.205f,0.3647f,0f,-0.125f,-0.3282f,0.5833f,0f,0.3619f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.5f,-0.005f,0.3019f,0f,0f,0.0789f,0f,0.698f,0f,0.0369f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,1f,-0.5175f,0f,0.4219f,0f,-0.0631f,-1.3003f,0f,0f,-0.125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:black_shulker_box",Properties:{}},transformation:[0f,0f,0.875f,-0.4425f,0f,0.403f,0f,-0.0625f,-0.5f,0f,0f,0.3125f,0f,0f,0f,1f]}]}"""
    ).map {
        it.scale(.8f)
    }
}


class SpiderModelPiece (
    var block: BlockData,
    var transformation: Matrix4f,
    var brightness: Display.Brightness?,
    var cloak: Boolean,
) {
    fun scale(factor: Float): SpiderModelPiece {
        transformation.set(Matrix4f().scale(factor).mul(transformation))
        return this
    }

    fun clone(): SpiderModelPiece {
        return SpiderModelPiece(
            block = block.clone(),
            transformation = Matrix4f(transformation),
            brightness = brightness?.let { Display.Brightness(it.blockLight, it.skyLight) },
            cloak = cloak
        )
    }
}

fun parseModelFromCommand(command: String): List<SpiderModelPiece> {
    val world = Bukkit.getWorlds().first()
    val location = Location(world, .0,.0,.0)

    runCommandSilently(
        location = location,
        command = "execute positioned ${location.x + .5} ${location.y} ${location.z + .5} run ${command.trimStart('/')}"
    )

    val out = mutableListOf<SpiderModelPiece>()

    val radius = 0.001
    for (entity in world.getNearbyEntities(location, radius, radius, radius)) {
        if (entity !is BlockDisplay) continue

        val transform = matrixFromTransform(entity.transformation)
//        val translation = entity.location.toVector().subtract(location.toVector())
//        transform.translate(translation.toVector3f())

        val info = SpiderModelPiece(
            block = entity.block,
            transformation = transform,
            brightness = entity.brightness,
            cloak = false
        )

        out += info

        if (entity.block.material == Material.CYAN_SHULKER_BOX) interval(0,5) {
            if (Random.nextBoolean()) return@interval
            info.block = listOf(
                Material.CYAN_SHULKER_BOX.createBlockData(),
                Material.CYAN_SHULKER_BOX.createBlockData(),
                Material.CYAN_SHULKER_BOX.createBlockData(),
                Material.CYAN_CONCRETE.createBlockData(),
                Material.CYAN_CONCRETE_POWDER.createBlockData(),

                Material.LIGHT_BLUE_SHULKER_BOX.createBlockData(),
                Material.LIGHT_BLUE_CONCRETE.createBlockData(),
                Material.LIGHT_BLUE_CONCRETE_POWDER.createBlockData(),
            ).random()
        }

        entity.remove()
    }

    return out
}
