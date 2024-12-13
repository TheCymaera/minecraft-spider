package com.heledron.spideranimation

import com.heledron.spideranimation.spider.misc.StayStillBehaviour
import com.heledron.spideranimation.spider.rendering.targetRenderEntity
import com.heledron.spideranimation.utilities.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    val closables = mutableListOf<Closeable>()

    fun writeAndSaveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
    }

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        closables.forEach { it.close() }
    }

    override fun onEnable() {
        currentPlugin = this

        closables += Closeable {
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
                if (spider.mount.getRider() == null) spider.behaviour = StayStillBehaviour(spider)
            }

            // render target
            val target = if (AppState.miscOptions.showLaser) AppState.target else null ?: AppState.chainVisualizer?.target
            if (target != null) AppState.renderer.render("target", targetRenderEntity(target))

            // flush renderer
            AppState.renderer.flush()

            AppState.target = null
        }


        closables += onSpawnEntity { entity, _ ->
            // Use this command to spawn a chain visualizer
            // /summon minecraft:area_effect_cloud ~ ~ ~ {Tags:["spider.chain_visualizer"]}
            if (!entity.scoreboardTags.contains("spider.chain_visualizer")) return@onSpawnEntity
            val location = entity.location
            AppState.chainVisualizer = if (AppState.chainVisualizer != null) null else KinematicChainVisualizer.create(3, 1.5, location)
            AppState.chainVisualizer?.detailed = AppState.showDebugVisuals
            entity.remove()
        }
    }
}