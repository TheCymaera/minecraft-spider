package com.heledron.spideranimation

import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.spider.misc.MoveAimlesslyBehaviour
import com.heledron.spideranimation.spider.misc.StayStillBehaviour
import com.heledron.spideranimation.spider.rendering.targetRenderEntity
import com.heledron.spideranimation.utilities.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    val closeables = mutableListOf<Closeable>()

    fun writeAndSaveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
    }

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        closeables.forEach { it.close() }
        AppState.closeables.forEach { it.close() }
    }

    override fun onEnable() {
        currentPlugin = this

        closeables += Closeable {
            AppState.spider?.close()
            AppState.chainVisualizer?.close()
            AppState.renderer.close()
        }

        logger.info("Enabling Spider Animation plugin")

//        config.getConfigurationSection("spider")?.getValues(true)?.let { AppState.options = Serializer.fromMap(it, SpiderOptions::class.java) }

        registerCommands(this)
        registerItems()

        onTick {
            // update spider
            val spider = AppState.spider
            if (spider != null) {
                spider.showDebugVisuals = AppState.showDebugVisuals
                spider.gallop = AppState.gallop

                spider.update()
                if (spider.mount.getRider() == null) {
                    spider.behaviour = StayStillBehaviour(spider)

                    val r = (0..20).random() > 1;
                    if(r) {
                        spider.behaviour = MoveAimlesslyBehaviour(spider)
                    }
                }
            }

            // render target
            val target = (if (AppState.miscOptions.showLaser) AppState.target else null) ?: AppState.chainVisualizer?.target
            if (target != null) AppState.renderer.render("target", targetRenderEntity(target))

            // flush renderer
            AppState.renderer.flush()

            AppState.target = null
        }


        closeables += onSpawnEntity { entity, _ ->
            // Use this command to spawn a chain visualizer
            // /summon minecraft:area_effect_cloud ~ ~ ~ {Tags:["spider.chain_visualizer"]}
            if (!entity.scoreboardTags.contains("spider.chain_visualizer")) return@onSpawnEntity
            val segmentPlans = AppState.options.bodyPlan.legs.lastOrNull()?.segments ?: return@onSpawnEntity

            AppState.chainVisualizer = if (AppState.chainVisualizer != null) null else KinematicChainVisualizer.create(
                segmentPlans = segmentPlans,
                root = entity.location.toVector(),
                world = entity.world,
                straightenRotation = AppState.options.walkGait.legStraightenRotation,
            )

            AppState.chainVisualizer?.detailed = AppState.showDebugVisuals
            entity.remove()
        }
    }
}