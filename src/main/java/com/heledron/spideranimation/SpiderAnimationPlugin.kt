package com.heledron.spideranimation

import com.google.gson.Gson
import com.heledron.spideranimation.spider.*
import com.heledron.spideranimation.items.CustomItemRegistry
import com.heledron.spideranimation.items.registerItems
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: SpiderAnimationPlugin
    }

    val closables = mutableListOf<Closeable>()

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        closables.forEach { it.close() }
    }

    override fun onEnable() {
        instance = this

        val targetRenderer = EntityRenderer<BlockDisplay>()

        closables += Closeable {
            AppState.spider?.close()
            AppState.chainVisualizer?.close()
            targetRenderer.close()
        }

        logger.info("Enabling Spider Animation plugin")

        val options = mapOf(
            "walk_gait" to { AppState.walkGait },
            "gallop_gait" to { AppState.gallopGait },
            "options" to { AppState.spiderOptions },
        )
        val defaultObjects = mapOf(
            "walk_gait" to { Gait.defaultWalk().apply { scale(AppState.walkGait.getScale()) } },
            "gallop_gait" to { Gait.defaultGallop().apply { scale(AppState.walkGait.getScale()) } },
            "options" to { SpiderOptions() },
        )

        config.getConfigurationSection("walk_gait")?.getValues(true)?.let { AppState.walkGait = Serializer.fromMap(it, Gait::class.java) }
        config.getConfigurationSection("gallop_gait")?.getValues(true)?.let { AppState.gallopGait = Serializer.fromMap(it, Gait::class.java) }
        config.getConfigurationSection("options")?.getValues(true)?.let { AppState.spiderOptions = Serializer.fromMap(it, SpiderOptions::class.java) }
        config.getConfigurationSection("body_plan")?.getValues(true)?.let { AppState.bodyPlan = Serializer.fromMap(it, SymmetricalBodyPlan::class.java) }

        registerItems()

        interval(0, 1) {
            AppState.update()

            AppState.spider?.let { spider ->
                spider.update()
                if (spider.mount.getRider() == null) spider.behaviour = StayStillBehaviour(spider)
            }

            val targetVal = AppState.target ?: AppState.chainVisualizer?.target
            if (targetVal != null) targetRenderer.render(targetTemplate(targetVal))
            else targetRenderer.close()

            AppState.target = null
        }

        // /summon minecraft:area_effect_cloud -26 -11 26 {Tags:["spider.chain_visualizer"]}
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
                if (!event.entity.scoreboardTags.contains("spider.chain_visualizer")) return

                val location = event.entity.location

                AppState.chainVisualizer?.close()
                AppState.chainVisualizer = if (AppState.chainVisualizer != null) null else KinematicChainVisualizer.create(3, 1.5, location)

                event.entity.remove()
            }
        }, this)

        getCommand("options")?.apply {
            setExecutor { sender, _, _, args ->
                val obj = options[args.getOrNull(0)]?.invoke() ?: return@setExecutor false
                val default = defaultObjects[args.getOrNull(0)]?.invoke() ?: return@setExecutor false
                val option = args.getOrNull(1) ?: return@setExecutor false
                val valueUnParsed = args.getOrNull(2)

                if (option == "reset") {
                    val map = Serializer.toMap(default) as Map<*, *>
                    Serializer.writeFromMap(obj, map)
                    sender.sendMessage("Reset all options")
                } else if (valueUnParsed == null) {
                    val value = Serializer.get(obj, option)
                    sender.sendMessage("Option $option is $value")
                } else if (valueUnParsed == "reset") {
                    val value = Serializer.get(default, option)
                    Serializer.set(obj, option, value)
                    sender.sendMessage("Reset option $option to $value")
                } else {
                    Serializer.setMap(obj, option, Gson().fromJson(valueUnParsed, Any::class.java))
                    val value = Serializer.get(obj, option)
                    sender.sendMessage("Set option $option to $value")
                }

                for ((key, value) in options) {
                    instance.config.set(key, Serializer.toMap(value()))
                }
                instance.saveConfig()

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                if (args.size == 1) {
                    return@setTabCompleter options.keys.filter { it.contains(args.last(), true) }
                }

                if (args.size == 2) {
                    val obj = options[args[0]]?.invoke() ?: return@setTabCompleter emptyList()
                    val map = Serializer.toMap(obj) as Map<*, *>
                    val keys = map.keys.map { it.toString() } + "reset"
                    return@setTabCompleter keys.filter { it.contains(args.last(), true) }
                }

                val obj = options[args[0]]?.invoke() ?: return@setTabCompleter emptyList()
                val map = Serializer.toMap(obj) as Map<*, *>
                val sample = map[args[1]]
                val keys = (if (sample is Boolean) listOf("true", "false") else emptyList()) + "reset"
                return@setTabCompleter keys.filter { it.contains(args.last(), true) }
            }
        }

        getCommand("fall")?.setExecutor { sender, _, _, args ->
            val spider = AppState.spider ?: return@setExecutor true

            val height = args[0].toDoubleOrNull()

            if (height == null) {
                sender.sendMessage("Usage: /spider:fall <height>")
                return@setExecutor true
            }

            spider.teleport(spider.location.clone().add(0.0, height, 0.0))


            return@setExecutor true
        }

        getCommand("body_plan")?.apply {
            val bodyPlanTypes = mapOf(
                "quadruped" to ::quadrupedBodyPlan,
                "hexapod" to ::hexapodBodyPlan,
                "octopod" to ::octopodBodyPlan
            )

            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false

                val scale = args.getOrNull(1)?.toDoubleOrNull() ?: 1.0

                val builder = bodyPlanTypes[option]
                if (builder == null) {
                    sender.sendMessage("Invalid body plan: $option")
                    return@setExecutor true
                }

                AppState.bodyPlan = builder().apply { scale(scale) }.create()

                val oldScale = AppState.walkGait.getScale()
                AppState.walkGait.scale(scale / oldScale)
                AppState.gallopGait.scale(scale / oldScale)

                instance.config.set("body_plan", Serializer.toMap(AppState.bodyPlan))
                for ((key, value) in options) {
                    instance.config.set(key, Serializer.toMap(value()))
                }
                instance.saveConfig()

                val spider = AppState.spider
                if (spider != null) {
                    AppState.spider = AppState.createSpider(spider.location)
                }

                sender.sendMessage("Set body plan to $option")

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                if (args.size == 1) {
                    return@setTabCompleter bodyPlanTypes.keys.filter { it.contains(args.last(), true) }
                }
                return@setTabCompleter emptyList()
            }
        }

        getCommand("items")?.setExecutor { sender, _, _, _ ->
            val player = sender as? org.bukkit.entity.Player ?: return@setExecutor true

            val inventory = Bukkit.createInventory(null, 9 * 3, "Items")
            for (item in CustomItemRegistry.items) {
                inventory.addItem(item.defaultItem.clone())
            }

            player.openInventory(inventory)


            return@setExecutor true
        }
    }
}