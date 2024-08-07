package com.heledron.spideranimation.spider

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

    fun weighted(vararg pairs: Pair<Material, Int>): List<Material> {
        val list = mutableListOf<Material>()
        for ((option, weight) in pairs) {
            for (i in 0 until weight) list.add(option)
        }
        return list
    }

    val mossLike = listOf(
        Material.GRASS_BLOCK,
        Material.OAK_LEAVES,
        Material.AZALEA_LEAVES,
        Material.MOSS_BLOCK, Material.MOSS_CARPET
    )

    if (mossLike.contains(material)) {
        return weighted(Material.MOSS_BLOCK to 4, Material.GREEN_SHULKER_BOX to 1)
    }
    if (material == Material.DIRT || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT) {
        return listOf(Material.COARSE_DIRT, Material.ROOTED_DIRT)
    }

    if (material == Material.STONE_BRICKS || material == Material.STONE_BRICK_SLAB || material == Material.STONE_BRICK_STAIRS) {
        return weighted(Material.STONE_BRICKS to 3, Material.CRACKED_STONE_BRICKS to 1, Material.LIGHT_GRAY_SHULKER_BOX to 1)
    }

    if (material == Material.OAK_LOG) {
        return listOf(Material.OAK_WOOD)
    }

    val spruceLike = listOf(
        Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD,
        Material.SPRUCE_PLANKS, Material.SPRUCE_SLAB, Material.SPRUCE_STAIRS, // Material.SPRUCE_FENCE, Material.SPRUCE_FENCE_GATE,
        Material.SPRUCE_TRAPDOOR, Material.SPRUCE_DOOR, Material.COMPOSTER, Material.BARREL
    )

    if (spruceLike.contains(material)) {
        return weighted(Material.SPRUCE_PLANKS to 3, Material.STRIPPED_SPRUCE_WOOD to 1)
    }

    if (material === Material.DIRT_PATH) {
        return listOf(Material.STRIPPED_OAK_WOOD)
    }

    if (material == Material.DEEPSLATE_TILES || material == Material.DEEPSLATE_BRICKS || material == Material.DEEPSLATE || material == Material.POLISHED_DEEPSLATE_SLAB) {
        return listOf(Material.DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES)
    }

    if (material === Material.SAND) {
        return weighted(Material.SAND to 4, Material.SANDSTONE to 1)
    }

    val deepSlateLike = listOf(
        Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE_SLAB, Material.POLISHED_DEEPSLATE_STAIRS, // Material.POLISHED_DEEPSLATE_WALL,
        Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_STAIRS, // Material.DEEPSLATE_TILE_WALL,
        Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_STAIRS, // Material.DEEPSLATE_BRICK_WALL,
        Material.ANVIL,
        Material.POLISHED_BASALT, Material.BASALT
    )

    if (deepSlateLike.contains(material)) {
        return weighted(Material.POLISHED_DEEPSLATE to 3, Material.DEEPSLATE_TILES to 1)
    }

    val stoneLike = listOf(
        Material.ANDESITE, Material.ANDESITE_SLAB, Material.ANDESITE_STAIRS, // Material.ANDESITE_WALL,
        Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE_SLAB, Material.POLISHED_ANDESITE_STAIRS,
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

    val copper = listOf(
        Material.WAXED_EXPOSED_COPPER, Material.WAXED_EXPOSED_CUT_COPPER,
        Material.WAXED_EXPOSED_COPPER_BULB, Material.WAXED_EXPOSED_CHISELED_COPPER,
        Material.WAXED_EXPOSED_CUT_COPPER_SLAB, Material.WAXED_EXPOSED_CUT_COPPER_STAIRS,
        Material.WAXED_EXPOSED_COPPER_TRAPDOOR, Material.LIGHTNING_ROD
    )

    if (copper.contains(material)) {
        return listOf(Material.WAXED_EXPOSED_COPPER, Material.WAXED_EXPOSED_CUT_COPPER)
    }

    if (material == Material.YELLOW_CONCRETE || material == Material.YELLOW_TERRACOTTA) {
        return listOf(Material.YELLOW_TERRACOTTA, Material.YELLOW_CONCRETE, Material.YELLOW_SHULKER_BOX)
    }

    val tuffLike = listOf(
        Material.TUFF, Material.TUFF_SLAB, Material.TUFF_STAIRS, Material.CHISELED_TUFF,
        Material.TUFF_BRICKS, Material.TUFF_BRICK_SLAB, Material.TUFF_BRICK_STAIRS,
        Material.POLISHED_TUFF, Material.POLISHED_TUFF_SLAB, Material.POLISHED_TUFF_STAIRS
    );

    if (tuffLike.contains(material)) {
        return weighted(Material.POLISHED_TUFF to 2, Material.GRAY_SHULKER_BOX to 1)
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