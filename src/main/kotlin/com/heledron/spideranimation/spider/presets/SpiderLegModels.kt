package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.utilities.parseModelFromCommand
import org.bukkit.Material

object SpiderLegModel {
    val BASE = parseModelFromCommand(
        """/summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.25f,-0.125f,0.1f,0f,0f,-0.125f,0f,1f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[-0.25f,0f,0f,0.125f,0f,0f,0.205f,-0.0625f,0f,1.1875f,0f,-0.1875f,0f,0f,0f,1f]}]}"""
    ).apply {
        pieces.forEach {
            it.tags += "base"
            it.tags += "leg"
        }
    }

    val FEMUR = parseModelFromCommand(
        """/summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.125f,-0.125f,-0.1f,0f,0f,0f,0f,-1f,0f,1f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[-0.205f,0f,0f,0.1025f,0f,0f,0.125f,0f,0f,1f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.125f,0f,-0.1f,0f,0f,0f,0f,-1f,0f,1f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,-0.25f,0.125f,0.1f,0f,0f,0.0125f,0f,-1f,0f,0.9981f,0f,0f,0f,1f]}]}"""
    ).apply {
        pieces.forEach {
            it.tags += "femur"
            it.tags += "leg"
        }
    }

    val TIBIA = parseModelFromCommand(
        """/summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,-0.248f,0f,0.124f,0.0739f,0f,-0.0739f,0.1494f,0.4722f,0f,0.4722f,-0.1541f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.25f,-0.125f,-0.0012f,0.1185f,0f,0.0325f,-0.9457f,-0.0827f,0f,0.875f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[-0.1425f,0f,0f,0.0625f,0f,0f,0.16f,-0.0925f,0f,0.375f,0f,0.625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_shulker_box",Properties:{}},transformation:[-0.08f,0f,0f,0.04f,0f,0f,0.08f,-0.155f,0f,0.5f,0f,0.25f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.0566f,0f,-0.0566f,-0.0119f,-0.0566f,0f,-0.0566f,0.0756f,0f,0.5f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.1425f,0f,0f,-0.0712f,0f,0f,0.16f,-0.1325f,0f,-0.3125f,0f,0.3125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.0566f,0f,0.0566f,-0.0688f,-0.0566f,0f,0.0566f,0.0187f,0f,-0.3125f,0f,0.8125f,0f,0f,0f,1f]}]}"""
    ).apply {
        pieces.forEach {
            it.tags += "tibia"
            it.tags += "leg"
            if (it.block.material == Material.SMOOTH_QUARTZ) it.tags += "cloak"
            if (it.tags.contains("cloak")) it.block = Material.WHITE_CONCRETE.createBlockData()
        }
    }

    val TIP = parseModelFromCommand(
        """/summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.1875f,-0.0938f,-0.0008f,0.1185f,0f,0.0193f,-0.619f,-0.0827f,0f,0.5627f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_shulker_box",Properties:{}},transformation:[0f,0f,0.0813f,-0.0406f,0.0813f,0f,0f,-0.0744f,0f,0.9375f,0f,0.0625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0f,0f,0.1705f,-0.085f,0f,-0.125f,0f,0.0625f,0.5f,0f,0f,0f,0f,0f,0f,1f]}]}"""
    ).apply {
        pieces.forEach {
            it.tags += "tip"
            it.tags += "leg"
            if (it.block.material == Material.SMOOTH_QUARTZ) it.tags += "cloak"
            if (it.tags.contains("cloak")) it.block = Material.WHITE_CONCRETE.createBlockData()
        }
    }
}
