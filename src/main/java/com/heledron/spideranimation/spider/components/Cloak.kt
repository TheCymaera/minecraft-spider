package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.CloakOptions
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.ECSEntity
import com.heledron.spideranimation.utilities.block_colors.findBlockWithColor
import com.heledron.spideranimation.utilities.block_colors.getBlockColor
import com.heledron.spideranimation.utilities.colors.Oklab
import com.heledron.spideranimation.utilities.colors.distanceTo
import com.heledron.spideranimation.utilities.colors.toOklab
import com.heledron.spideranimation.utilities.deprecated.SeriesScheduler
import com.heledron.spideranimation.utilities.deprecated.raycastGround
import com.heledron.spideranimation.utilities.events.runLater
import com.heledron.spideranimation.utilities.eyePosition
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import java.util.WeakHashMap

class CloakDamageEvent(val entity: ECSEntity, val spider: SpiderBody, val cloak: Cloak)

class CloakToggleEvent(val entity: ECSEntity, val spider: SpiderBody)

class Cloak(var options: CloakOptions) {
    var active = false
    private var cloakColor = WeakHashMap<Any, Oklab>()
    private var cloakOverride = WeakHashMap<Any, BlockData>()
    private var cloakGlitching = false

    fun toggleCloak(app: ECS, entity: ECSEntity) {
        val spider = entity.query<SpiderBody>() ?: return
        active = !active
        app.emit(CloakToggleEvent(entity = entity, spider = spider))
    }

    fun getPiece(id: Any, world: World, position: Vector, originalBlock: BlockData, originalBrightness: Display.Brightness?): Pair<BlockData, Display.Brightness?> {
        applyCloak(id, world, position, originalBlock, originalBrightness?.skyLight ?: 15)

        val override = cloakOverride[id]
        if (override != null) return override to Display.Brightness(0, 15)

        val cloakColor = cloakColor[id] ?: return originalBlock to originalBrightness
        val match = findBlockWithColor(cloakColor.toRGB(), options.allowCustomBrightness)
        return match.block to Display.Brightness(0, match.brightness)
    }

    private fun applyCloak(id: Any, world: World, position: Vector, originalBlock: BlockData, originalBrightness: Int) {
        if (cloakGlitching) return

        fun groundCast(): RayTraceResult? {
            return world.raycastGround(position, DOWN_VECTOR, 5.0)
        }

        fun cast(): RayTraceResult? {
            val targetPlayer = Bukkit.getOnlinePlayers().firstOrNull() ?: return groundCast()

            val direction = position.clone().subtract(targetPlayer.eyePosition)
            return world.raycastGround(position, direction, 30.0)
        }

        val originalColor = getBlockColor(originalBlock, originalBrightness)?.toOklab() ?: return
        val currentColor = cloakColor[id] ?: originalColor

        val targetColor = run getTargetColor@{
            if (!active) return@getTargetColor originalColor

            val rayTrace = cast() ?: return@getTargetColor currentColor
            val block = rayTrace.hitBlock?.blockData ?: return@getTargetColor currentColor
            val lightLevel = 15
            getBlockColor(block, lightLevel)?.toOklab() ?: currentColor
        }


        val newColor = currentColor
            .lerp(targetColor, options.lerpSpeed.toFloat())
            .moveTowards(targetColor, options.moveSpeed.toFloat())

        if (newColor == originalColor) cloakColor.remove(id)
        else cloakColor[id] = newColor
    }


    fun breakCloak() {
        cloakGlitching = true

        val originalColors = cloakColor.values.toList()

        val glitch = listOf(
            { id: Any -> cloakOverride[id] = Material.LIGHT_BLUE_GLAZED_TERRACOTTA.createBlockData() },
            { id: Any -> cloakOverride[id] = Material.CYAN_GLAZED_TERRACOTTA.createBlockData() },
            { id: Any -> cloakOverride[id] = Material.WHITE_GLAZED_TERRACOTTA.createBlockData() },
            { id: Any -> cloakOverride[id] = Material.GRAY_GLAZED_TERRACOTTA.createBlockData() },

            { id: Any -> cloakOverride[id] = null },
            { id: Any -> cloakColor[id] = originalColors.random() },
        )

        var maxTime = 0

        for ((id) in cloakColor) {
            val scheduler = SeriesScheduler()

            fun randomSleep(min: Int, max: Int) {
                scheduler.sleep((min + Math.random() * (max - min)).toLong())
            }

            randomSleep(0, 3)
            for (i in 0 until (Math.random() * 4).toInt()) {
                scheduler.run { glitch.random()(id) }
                scheduler.sleep(2L)
            }

            scheduler.run {
                cloakColor[id] = null
                cloakOverride[id] = null
            }

            if (Math.random() < 1.0 / 6) continue

            randomSleep(0, 3)

            for (i in 0 until  (Math.random() * 3).toInt()) {
                scheduler.run {
                    cloakOverride[id] = findBlockWithColor(originalColors.random().toRGB(), options.allowCustomBrightness).block
                }

                randomSleep(5, 15)

                scheduler.run {
                    cloakOverride[id] = null
                }
                scheduler.sleep(2L)
            }

            if (scheduler.time > maxTime) maxTime = scheduler.time.toInt()
        }

        runLater(maxTime.toLong()) {
            cloakGlitching = false
        }
    }
}

private fun Oklab.moveTowards(target: Oklab, maxDelta: Float): Oklab {
    val delta = this.distanceTo(target)
    if (delta <= maxDelta) return target
    val t = maxDelta / delta
    return this.lerp(target, t)
}

fun setupCloak(app: ECS) {
    app.onEvent<TridentHitEvent> { event ->
        val cloak = event.entity.query<Cloak>() ?: return@onEvent
        if (cloak.active) {
            app.emit(CloakDamageEvent(entity = event.entity, spider = event.spider, cloak = cloak))
            cloak.breakCloak()
        }
        cloak.active = false
    }
}