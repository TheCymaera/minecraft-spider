package com.heledron.spideranimation.components

import com.heledron.spideranimation.*
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import java.io.Closeable
import java.lang.Math
import kotlin.collections.ArrayList
import kotlin.math.abs

class SpiderEntityRenderer(val spider: Spider): SpiderComponent {
    val closeables = ArrayList<Closeable>()

    var cloakGlitching = false

    val defaultMaterial = Material.NETHERITE_BLOCK

    val renderer = MultiEntityRenderer()

    init {
        // Break cloak
        closeables += spider.cloak.onCloakDamaged.listen {
            breakCloak()
        }

        closeables += renderer
    }

    override fun render() {
        renderer.beginRender()

        for ((legIndex, leg) in spider.body.legs.withIndex()) {
            // up vector is the cross product of the y-axis and the end-effector direction
            fun segmentUpVector(): Vector {
                val direction = leg.chain.getEndEffector().clone().subtract(leg.chain.root)
                return direction.clone().crossProduct(Vector(0, 1, 0))
            }

            val segmentUpVector = segmentUpVector()

            // Render leg segment
            for ((segmentIndex, segment) in leg.chain.segments.withIndex()) {
                val parent = leg.chain.segments.getOrNull(segmentIndex - 1)?.position ?: leg.chain.root
                val vector = segment.position.clone().subtract(parent).normalize().multiply(segment.length)
                val location = parent.toLocation(spider.location.world!!)

                renderer.render(Pair(legIndex, segmentIndex), lineTemplate(
                    location = location,
                    vector = vector,
                    thickness = leg.legPlan.segments[segmentIndex].thickness.toFloat(),
                    upVector = segmentUpVector,
                    init = { it.block = defaultMaterial.createBlockData() },
                    update = {
                        if (!cloakGlitching) {
                            val centre = location.clone().add(vector.clone().multiply(0.5))
                            val otherSegments = renderer.rendered.filter { entry ->
                                val key = entry.key as? Pair<*, *> ?: return@filter false
                                key.first == legIndex && key.second != segmentIndex
                            }
                            applyCloak(it, spider, centre, otherSegments.mapNotNull { entry -> entry.value as? BlockDisplay })
                        }
                    }
                ))
            }
        }

        renderer.finishRender()
    }

    override fun close() {
        closeables.forEach { it.close() }
    }

    private fun breakCloak() {
        cloakGlitching = true

        // get entities with original block
        val segmentEntities = renderer.rendered.mapNotNull {
            val entity = it.value as? BlockDisplay ?: return@mapNotNull null
            Pair(entity, entity.block.material)
        }

        var maxTime = 0
        for ((segment, originalBlock) in segmentEntities) {
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
                defaultMaterial,
                originalBlock
            )

            randomSleep(0, 3)
            for (i in 0 until (Math.random() * 4).toInt()) {
                val transitionBlock = glitchBlocks[(Math.random() * glitchBlocks.size).toInt()]

                scheduler.run {
                    segment.block = transitionBlock.createBlockData()
                    if (Math.random() < .5) {
                        spawnParticle(Particle.FISHING, segment.location, (1 * Math.random()).toInt(), .3, .3, .3, 0.0)
                    }
                }

                scheduler.sleep(2L)
            }

            scheduler.run { segment.block = defaultMaterial.createBlockData() }

            if (Math.random() < 1.0 / 6) continue

            randomSleep(0, 3)

            for (i in 0 until  (Math.random() * 3).toInt()) {
                scheduler.run {
                    val randomBlock = segmentEntities[(Math.random() * segmentEntities.size).toInt()].second
                    segment.block = randomBlock.createBlockData()
                }

                randomSleep(5, 15)

                scheduler.run { segment.block = defaultMaterial.createBlockData() }
                scheduler.sleep(2L)
            }

            if (scheduler.time > maxTime) maxTime = scheduler.time.toInt()
        }

        runLater(maxTime.toLong()) {
            cloakGlitching = false
        }
    }

    fun applyCloak(entity: BlockDisplay, spider: Spider, centre: Location, otherSegments: List<BlockDisplay>) {
        if (spider.cloak.active) {
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
                    val hash = abs(entity.location.x.toInt() + entity.location.z.toInt())
                    val choice = palette[hash % palette.size]

                    if (entity.block.material !== choice) {
                        val alreadyInPalette = palette.contains(entity.block.material)
                        val doGlitch = Math.random() < 1.0 / 2 || entity.block.material == defaultMaterial

                        val waitTime = if (alreadyInPalette || !doGlitch) 0 else (Math.random() * 3).toInt()
                        val glitchTime = if (alreadyInPalette || !doGlitch) 0 else (Math.random() * 3).toInt()

                        transitionSegmentBlock(entity, waitTime, glitchTime, choice)
                    }
                } else {
                    // take block from another segment
                    val other = otherSegments
                        .firstOrNull { it.block.material != defaultMaterial }
                        ?: otherSegments.firstOrNull()

                    if (other != null && entity.block.material != other.block.material) {
                        transitionSegmentBlock(
                            entity,
                            (Math.random() * 3).toInt(),
                            (Math.random() * 3).toInt(),
                            other.block.material
                        )
                    }
                }
            }
        } else {
            if (entity.block.material != defaultMaterial) {
                transitionSegmentBlock(
                    entity,
                    (Math.random() * 3).toInt(),
                    (Math.random() * 3).toInt(),
                    defaultMaterial
                )
            }
        }
    }

    companion object {
        val transitioningSegments = ArrayList<BlockDisplay>()
        fun transitionSegmentBlock(segment: BlockDisplay, waitTime: Int, glitchTime: Int, newBlock: Material) {
            if (transitioningSegments.contains(segment)) return
            transitioningSegments.add(segment)

            val scheduler = SeriesScheduler()
            scheduler.sleep(waitTime.toLong())
            scheduler.run {
                segment.block = Material.GRAY_GLAZED_TERRACOTTA.createBlockData()
            }

            scheduler.sleep(glitchTime.toLong())
            scheduler.run {
                segment.block = newBlock.createBlockData();
                transitioningSegments.remove(segment)
            }
        }
    }
}
