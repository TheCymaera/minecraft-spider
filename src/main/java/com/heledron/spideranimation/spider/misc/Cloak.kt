package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.block_colors.getBestMatchFromColor
import com.heledron.spideranimation.utilities.block_colors.getColorFromBlock
import org.bukkit.*
import org.bukkit.Color
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import java.util.WeakHashMap

class Cloak(var  spider: Spider) : SpiderComponent {
    var active = false
    val onCloakDamage = EventEmitter()
    val onToggle = EventEmitter()

    // I'm using vectors so that we can do high-precision lerping.
    // see toVector and toColor functions below.
    private var cloakColor = WeakHashMap<Any, Vector>()
    private var cloakOverride = WeakHashMap<Any, BlockData>()
    private var cloakGlitching = false

    init {
        spider.tridentDetector.onHit.listen {
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

    fun getPiece(id: Any, position: Vector, originalBlock: BlockData, originalBrightness: Display.Brightness?): Pair<BlockData, Display.Brightness?> {
        applyCloak(id, position, originalBlock)

        val override = cloakOverride[id]
        if (override != null) return override to Display.Brightness(0, 15)

        val cloakColor = cloakColor[id] ?: return originalBlock to originalBrightness
        val match = getBestMatchFromColor(cloakColor.toColor(), spider.options.cloak.allowCustomBrightness)
        return match.block to Display.Brightness(0, match.brightness)
    }

    private fun applyCloak(id: Any, position: Vector, originalBlock: BlockData) {
        val location = position.toLocation(spider.world)

        if (cloakGlitching) return

        fun groundCast(): RayTraceResult? {
            return raycastGround(location, DOWN_VECTOR, 5.0)
        }

        fun cast(): RayTraceResult? {
            val targetPlayer = Bukkit.getOnlinePlayers().firstOrNull() ?: return groundCast()

            val direction = location.toVector().subtract(targetPlayer.eyeLocation.toVector())
            val rayCast = raycastGround(location, direction, 30.0)
            return rayCast
        }

        fun getTargetBlock(): BlockData {
            if (!active) return originalBlock
            val rayTrace = cast() ?: return originalBlock
            return rayTrace.hitBlock?.blockData ?: originalBlock
        }

        val targetBlock = getTargetBlock()
        val targetColor = getColorFromBlock(targetBlock) ?: return
        val originalColor = getColorFromBlock(originalBlock)?.toVector() ?: return
        val currentColor = cloakColor[id] ?: originalColor

        val newColor = currentColor.clone()
            .lerp(targetColor.toVector(), spider.options.cloak.lerpSpeed)
            .moveTowards(targetColor.toVector(), spider.options.cloak.moveSpeed)

        if (newColor == originalColor) cloakColor.remove(id)
        else cloakColor[id] = newColor
    }


    private fun breakCloak() {
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
                    cloakOverride[id] = getBestMatchFromColor(originalColors.random().toColor(), spider.options.cloak.allowCustomBrightness).block
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


//    val transitioning = ArrayList<Any>()
//    fun transitionSegmentBlock(id: Any, waitTime: Int, glitchTime: Int, newBlock: Material?) {
//        if (transitioning.contains(id)) return
//        transitioning.add(id)
//
//        val scheduler = SeriesScheduler()
//        scheduler.sleep(waitTime.toLong())
//        scheduler.run {
//            cloakOverride[id] = Material.GRAY_GLAZED_TERRACOTTA.createBlockData()
//        }
//
//        scheduler.sleep(glitchTime.toLong())
//        scheduler.run {
//            cloakColor[id] = newBlock
//            transitioning.remove(id)
//        }
//    }
}


private fun Color.toVector(): Vector {
    return Vector(this.red, this.green, this.blue)
}

private fun Vector.toColor(): Color {
    return Color.fromRGB(x.toInt(), y.toInt(), z.toInt())
}