package com.heledron.spideranimation.components

import com.heledron.spideranimation.*
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Trident

class Cloak(var  spider: Spider) : SpiderComponent {
    var active = false
    var onGetHitByTrident = EventEmitter()
    var onCloakDamaged = EventEmitter()
    var onToggle = EventEmitter()

    fun toggleCloak() {
        active = !active
        onToggle.emit()
    }

    override fun update() {
        val tridents = spider.location.world!!.getNearbyEntities(spider.location, 1.5, 1.5, 1.5) {
            it is Trident && it.shooter != spider.mount.getRider()
        }
        for (trident in tridents) {
            if (trident != null && trident.velocity.length() > 2.0) {
                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                onGetHitByTrident.emit()
                if (active) onCloakDamaged.emit()
                active = false

                spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))
            }
        }
    }
}


fun getCloakPalette(material: Material): List<Material> {
    if (material == Material.GRASS_BLOCK || material == Material.OAK_LEAVES || material == Material.AZALEA_LEAVES || material == Material.MOSS_CARPET) {
        return listOf(Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.GREEN_SHULKER_BOX)
    }
    if (material == Material.DIRT || material == Material.COARSE_DIRT) {
        return listOf(Material.COARSE_DIRT, Material.DIRT)
    }

    if (material == Material.STONE_BRICKS || material == Material.STONE_BRICK_SLAB || material == Material.STONE_BRICK_STAIRS) {
        return listOf(Material.STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.LIGHT_GRAY_SHULKER_BOX)
    }

    if (material == Material.OAK_LOG) {
        return listOf(Material.OAK_WOOD)
    }

    if (material == Material.SPRUCE_TRAPDOOR || material == Material.SPRUCE_DOOR || material == Material.COMPOSTER || material == Material.BARREL) {
        return listOf(Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_WOOD)
    }


//    if (material == Material.DARK_OAK_FENCE) {
//        return listOf(Material.DARK_OAK_PLANKS)
//    }

    if (material === Material.DIRT_PATH) {
        return listOf(Material.STRIPPED_OAK_WOOD)
    }

    if (material == Material.DEEPSLATE_TILES || material == Material.DEEPSLATE_BRICKS || material == Material.DEEPSLATE) {
        return listOf(Material.DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES)
    }

    if (material === Material.SAND) {
        return listOf(Material.SAND, Material.SAND, Material.SAND, Material.SAND, Material.SANDSTONE)
    }

    val deepSlateLike = listOf(
        Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE_SLAB, Material.POLISHED_DEEPSLATE_STAIRS, // Material.POLISHED_DEEPSLATE_WALL,
        Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_STAIRS, // Material.DEEPSLATE_TILE_WALL,
        Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_STAIRS, // Material.DEEPSLATE_BRICK_WALL,
        Material.ANVIL
    )

    if (deepSlateLike.contains(material)) {
        return listOf(Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES)
    }

    val stoneLike = listOf(
        Material.ANDESITE, Material.ANDESITE_SLAB, Material.ANDESITE_STAIRS, // Material.ANDESITE_WALL,
        Material.COBBLESTONE, Material.COBBLESTONE_SLAB, Material.COBBLESTONE_STAIRS, // Material.COBBLESTONE_WALL,
        Material.STONE, Material.STONE_SLAB, Material.STONE_STAIRS,
        Material.GRAVEL,
    )

    if (stoneLike.contains(material)) {
        return listOf(Material.ANDESITE, Material.ANDESITE, Material.GRAVEL)
    }

    if (material == Material.NETHERITE_BLOCK) {
        return listOf(Material.NETHERITE_BLOCK)
    }

    if (material == Material.DARK_PRISMARINE || material == Material.DARK_PRISMARINE_SLAB || material == Material.DARK_PRISMARINE_STAIRS) {
        return listOf(Material.DARK_PRISMARINE)
    }

    if (material == Material.MUD_BRICKS || material == Material.MUD_BRICK_SLAB || material == Material.MUD_BRICK_STAIRS/* || material == Material.MUD_BRICK_WALL*/) {
        return listOf(Material.MUD_BRICKS)
    }

    if (material == Material.RED_CONCRETE) {
        return listOf(material)
    }

    return listOf()
}


fun getCloakSkyBlock(world: World): Material {
    val time = world.time

    if (time in 13188..22812) {
        return Material.BLACK_CONCRETE
    }

    if (time in 12542..23460) {
        return Material.CYAN_CONCRETE
    }

    return Material.LIGHT_BLUE_CONCRETE
}