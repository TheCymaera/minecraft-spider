package com.heledron.spideranimation

import com.heledron.spideranimation.AppState.ecs
import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.kinematic_chain_visualizer.setupChainVisualizer
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Mountable
import com.heledron.spideranimation.spider.components.SpiderBehaviour
import com.heledron.spideranimation.spider.components.StayStillBehaviour
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.spider.components.rendering.renderTarget
import com.heledron.spideranimation.spider.setupSpider
import com.heledron.spideranimation.utilities.ECSEntity
import com.heledron.spideranimation.utilities.events.onSpawnEntity
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.setupCoreUtils
import com.heledron.spideranimation.utilities.shutdownCoreUtils
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    fun writeAndSaveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
    }

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        shutdownCoreUtils()
    }

    override fun onEnable() {
        logger.info("Enabling Spider Animation plugin")

        setupCoreUtils()

        setupCommands(this)
        setupItems()
        setupSpider(ecs)
        setupChainVisualizer(ecs)

        ecs.run()
        onTick {
            ecs.update()
            ecs.render()
        }

        onTick {
            // update spider
            ecs.query<ECSEntity, SpiderBody>().forEach { (entity, spider) ->
                val mount = entity.query<Mountable>()
                if (mount !== null && mount.getRider() == null) {
                    entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
                }

                val renderer = entity.query<SpiderRenderer>()
                renderer?.renderDebugVisuals = AppState.renderDebugVisuals

                spider.gallop = AppState.gallop
            }

            // render target
            val target =
                (if (AppState.miscOptions.showLaser) AppState.target else null) ?:
                ecs.query<KinematicChainVisualizer>().firstOrNull()?.target

            if (target != null) renderTarget(target).submit("target")

            AppState.target = null
        }


        onSpawnEntity { entity ->
            // Use this command to spawn a chain visualizer
            // /summon minecraft:area_effect_cloud ~ ~ ~ {Tags:["spider.chain_visualizer"]}
            if (!entity.scoreboardTags.contains("spider.chain_visualizer")) return@onSpawnEntity

            val oldVisualizer = ecs.query<ECSEntity, KinematicChainVisualizer>().firstOrNull()?.first
            if (oldVisualizer == null) {
                AppState.createChainVisualizer(entity.location)
            } else {
                oldVisualizer.remove()
            }

            entity.remove()
        }
    }
}