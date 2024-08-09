package com.heledron.spideranimation

import com.google.gson.Gson
import com.heledron.spideranimation.spider.*
import com.heledron.spideranimation.utilities.CustomItemRegistry
import com.heledron.spideranimation.utilities.MultiModelRenderer
import com.heledron.spideranimation.utilities.Serializer
import com.heledron.spideranimation.utilities.interval
import org.bukkit.*
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

        val renderer = MultiModelRenderer()

        closables += Closeable {
            AppState.spider?.close()
            AppState.chainVisualizer?.close()
            renderer.close()
        }

        logger.info("Enabling Spider Animation plugin")

        val options = mapOf(
            "walk_gait" to { AppState.walkGait },
            "gallop_gait" to { AppState.gallopGait },
            "debug_options" to { AppState.debugOptions },
            "body_plan" to { AppState.bodyPlan }
        )
        val defaultObjects = mapOf(
            "walk_gait" to { Gait.defaultWalk().apply { scale(AppState.bodyPlan.storedScale) } },
            "gallop_gait" to { Gait.defaultGallop().apply { scale(AppState.bodyPlan.storedScale) } },
            "debug_options" to { SpiderDebugOptions() },
            "body_plan" to { quadrupedBodyPlan(segmentCount = 3, segmentLength = 1.0) }
        )

        config.getConfigurationSection("walk_gait")?.getValues(true)?.let { AppState.walkGait = Serializer.fromMap(it, Gait::class.java) }
        config.getConfigurationSection("gallop_gait")?.getValues(true)?.let { AppState.gallopGait = Serializer.fromMap(it, Gait::class.java) }
        config.getConfigurationSection("options")?.getValues(true)?.let { AppState.debugOptions = Serializer.fromMap(it, SpiderDebugOptions::class.java) }
        config.getConfigurationSection("body_plan")?.getValues(true)?.let { AppState.bodyPlan = Serializer.fromMap(it, BodyPlan::class.java) }

        registerItems()

        interval(0, 1) {
            AppState.update()

            AppState.spider?.let { spider ->
                spider.update()
                if (spider.mount.getRider() == null) spider.behaviour = StayStillBehaviour(spider)
            }

            (AppState.target ?: AppState.chainVisualizer?.target)?.let { target ->
                renderer.render("target", targetModel(target))
            }


            renderer.flush()

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

                fun printable(obj: Any?) = Gson().toJson(obj)

                if (option == "reset") {
                    val map = Serializer.toMap(default) as Map<*, *>
                    Serializer.writeFromMap(obj, map)
                    sender.sendMessage("Reset all options")
                } else if (valueUnParsed == null) {
                    val value = Serializer.get(obj, option)
                    sender.sendMessage("Option $option is ${printable(value)}")
                } else if (valueUnParsed == "reset") {
                    val value = Serializer.get(default, option)
                    Serializer.set(obj, option, value)
                    sender.sendMessage("Reset option $option to ${printable(value)}")
                } else {
                    val parsed = try {
                        Gson().fromJson(valueUnParsed, Any::class.java)
                    } catch (e: Exception) {
                        sender.sendMessage("Could not parse: $valueUnParsed")
                        return@setExecutor true
                    }
                    Serializer.setMap(obj, option, parsed)
                    val value = Serializer.get(obj, option)
                    sender.sendMessage("Set option $option to ${printable(value)}")
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
                    val map = Serializer.toMap(obj)

                    // get keys recursively
                    val current = args.last()
                    fun getKeys(obj: Any?, output: MutableList<String>, prefix: String = "") {
                        // hide items on the next "layer"
                        val suffix = prefix.slice(current.length + 1 until prefix.length)
                        if (suffix.contains(".") || suffix.contains("[")) return

                        if (prefix.isNotEmpty()) output.add(prefix)

                        if (obj is Map<*, *>) {
                            for ((key, value) in obj) getKeys(value, output, if (prefix.isNotEmpty()) "$prefix.$key" else key.toString())
                        }
                        if (obj is List<*>) {
                            for ((index, value) in obj.withIndex()) getKeys(value, output, "$prefix[$index]")
                        }
                    }

                    val keys = mutableListOf<String>()
                    getKeys(map, keys)

                    return@setTabCompleter keys.filter { it.startsWith(current, true) }
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
                "biped" to ::bipedBodyPlan,
                "quadruped" to ::quadrupedBodyPlan,
                "hexapod" to ::hexapodBodyPlan,
                "octopod" to ::octopodBodyPlan
            )

            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false

                val scale = args.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                val segmentCount = args.getOrNull(2)?.toIntOrNull() ?: 3
                val segmentLength = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0

                val bodyPlan = bodyPlanTypes[option]
                if (bodyPlan == null) {
                    sender.sendMessage("Invalid body plan: $option")
                    return@setExecutor true
                }

                val oldScale = AppState.bodyPlan.storedScale
                AppState.bodyPlan = bodyPlan(segmentCount, segmentLength).apply { scale(scale) }

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