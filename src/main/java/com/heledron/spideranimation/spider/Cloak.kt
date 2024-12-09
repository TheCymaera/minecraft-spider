package com.heledron.spideranimation.spider

import com.heledron.spideranimation.utilities.*
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.util.RayTraceResult
import java.util.WeakHashMap
import kotlin.math.abs

class Cloak(var  spider: Spider) : SpiderComponent {
    var active = false
    val onCloakDamage = EventEmitter()
    val onToggle = EventEmitter()

    private var cloakMaterial = WeakHashMap<Any, Material>()
    private var cloakGlitching = false

    init {
        spider.body.onGetHitByTrident.listen {
            if (active) onCloakDamage.emit()
            active = false
        }

        onCloakDamage.listen {
            breakCloak()
        }
    }

    override fun update() {
        // no nothing
    }

    fun toggleCloak() {
        active = !active
        onToggle.emit()
    }

    fun getPiece(id: Any, location: Location): BlockData? {
        applyCloak(id, location)
        return cloakMaterial[id]?.createBlockData()
    }


    private fun breakCloak() {
        cloakGlitching = true

        val originalMaterials = cloakMaterial.toList()

        var maxTime = 0
        for ((id, entry) in originalMaterials) {
            val scheduler = SeriesScheduler()

            fun randomSleep(min: Int, max: Int) {
                scheduler.sleep((min + Math.random() * (max - min)).toLong())
            }

            val glitchBlocks = listOf(
                Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
//                Material.BLUE_GLAZED_TERRACOTTA,
                Material.CYAN_GLAZED_TERRACOTTA,
                Material.WHITE_GLAZED_TERRACOTTA,
                Material.GRAY_GLAZED_TERRACOTTA,
                null,
                entry
            )

            randomSleep(0, 3)
            for (i in 0 until (Math.random() * 4).toInt()) {
                val transitionBlock = glitchBlocks[(Math.random() * glitchBlocks.size).toInt()]

                scheduler.run {
//                    cloakMaterial[id]?.material = transitionBlock
//                    if (Math.random() < .5) {
//                        val location = (chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root).toLocation(spider.location.world!!)
//                        spawnParticle(Particle.FISHING, location, (1 * Math.random()).toInt(), .3, .3, .3, 0.0)
//                    }
                }

                scheduler.sleep(2L)
            }

            scheduler.run { cloakMaterial[id] = null }

            if (Math.random() < 1.0 / 6) continue

            randomSleep(0, 3)

            for (i in 0 until  (Math.random() * 3).toInt()) {
                scheduler.run {
                    val randomBlock = originalMaterials[(Math.random() * originalMaterials.size).toInt()].second
                    cloakMaterial[id] = randomBlock
                }

                randomSleep(5, 15)

                scheduler.run { cloakMaterial[id] = null }
                scheduler.sleep(2L)
            }

            if (scheduler.time > maxTime) maxTime = scheduler.time.toInt()
        }

        runLater(maxTime.toLong()) {
            cloakGlitching = false
        }
    }

    private fun applyCloak(id: Any, centre: Location) {
        val currentMaterial = cloakMaterial[id]

        if (!spider.cloak.active) {
            if (currentMaterial != null) {
                transitionSegmentBlock(
                    id,
                    (Math.random() * 3).toInt(),
                    (Math.random() * 3).toInt(),
                    null
                )
            }
            return
        }

        fun groundCast(): RayTraceResult? {
            return raycastGround(centre, DOWN_VECTOR, 5.0)
        }

        fun cast(): RayTraceResult? {
            val targetPlayer = Bukkit.getOnlinePlayers().firstOrNull()
            if (targetPlayer != null) {
                val direction = centre.toVector().subtract(targetPlayer.eyeLocation.toVector())
                val rayCast = raycastGround(centre, direction, 30.0)
                if (rayCast != null) return rayCast
            }
            return groundCast()
        }

        val rayTrace = cast()
        if (rayTrace != null) {
            val palette = getCloakPalette(rayTrace.hitBlock!!.blockData.material)
            if (palette.isNotEmpty()) {
                val hash = abs(centre.x.toInt() + centre.z.toInt())
                val choice = palette[hash % palette.size]

                if (currentMaterial !== choice) {
                    val alreadyInPalette = palette.contains(currentMaterial)
                    val doGlitch = Math.random() < 1.0 / 2 || currentMaterial == null

                    val waitTime = if (alreadyInPalette || !doGlitch) 0 else (Math.random() * 3).toInt()
                    val glitchTime = if (alreadyInPalette || !doGlitch) 0 else (Math.random() * 3).toInt()

                    transitionSegmentBlock(id, waitTime, glitchTime, choice)
                }
            } else {
//                // take block from another segment
//                val other = otherSegments
//                    .firstOrNull { cloakMaterial[it] != null }
//                    ?: otherSegments.firstOrNull()
//
//                val otherMaterial = cloakMaterial[other]
//
//                if (other != null && currentMaterial != otherMaterial) {
//                    transitionSegmentBlock(
//                        segment,
//                        (Math.random() * 3).toInt(),
//                        (Math.random() * 3).toInt(),
//                        otherMaterial
//                    )
//                }
            }
        }
    }


    val transitioning = ArrayList<Any>()
    fun transitionSegmentBlock(id: Any, waitTime: Int, glitchTime: Int, newBlock: Material?) {
        if (transitioning.contains(id)) return
        transitioning.add(id)

        val scheduler = SeriesScheduler()
        scheduler.sleep(waitTime.toLong())
        scheduler.run {
            cloakMaterial[id] = Material.GRAY_GLAZED_TERRACOTTA
        }

        scheduler.sleep(glitchTime.toLong())
        scheduler.run {
            cloakMaterial[id] = newBlock
            transitioning.remove(id)
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