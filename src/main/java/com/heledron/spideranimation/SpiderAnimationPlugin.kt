package com.heledron.spideranimation

import com.google.gson.Gson
import com.heledron.spideranimation.spider.configuration.*
import com.heledron.spideranimation.spider.misc.StayStillBehaviour
import com.heledron.spideranimation.spider.presets.*
import com.heledron.spideranimation.spider.rendering.BlockDisplayModelPiece
import com.heledron.spideranimation.spider.rendering.targetModel
import com.heledron.spideranimation.utilities.*
import org.bukkit.Bukkit.createInventory
import org.bukkit.Material
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

        fun saveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
        }

//        config.getConfigurationSection("spider")?.getValues(true)?.let { AppState.options = Serializer.fromMap(it, SpiderOptions::class.java) }

        registerItems()

        interval(0, 1) {
            AppState.update()

            AppState.spider?.let { spider ->
                spider.update()
                if (spider.mount.getRider() == null) spider.behaviour = StayStillBehaviour(spider)
            }

            ((if (AppState.miscOptions.showLaser) AppState.target else null) ?: AppState.chainVisualizer?.target)?.let { target ->
                renderer.render("target", targetModel(target))
            }


            renderer.flush()

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

        getCommand("options")?.apply {
            val options = mapOf(
                "walkGait" to { AppState.options.walkGait },
                "gallopGait" to { AppState.options.gallopGait },
                "debug" to { AppState.options.debug },
                "misc" to { AppState.miscOptions },
            )

            val defaultObjects = mapOf(
                "walkGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.walkGait },
                "gallopGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.gallopGait },
                "debug" to { SpiderDebugOptions() },
                "misc" to { MiscellaneousOptions() },
            )

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

                saveConfig()

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

        fun getPieces(mode: String): List<BlockDisplayModelPiece> {
            val legPieces = AppState.options.bodyPlan.legs.flatMap { it.segments }.flatMap { it.model.pieces }
            val bodyPieces = AppState.options.bodyPlan.bodyModel.pieces

            if (mode == "body") {
                return bodyPieces
            } else if (mode == "legs") {
                return legPieces
            } else {
                return legPieces + bodyPieces
            }
        }

        getCommand("replace_material")?.apply {
            setExecutor { _, _, _, args ->
                val mode = args.getOrNull(0) ?: return@setExecutor false

                val from = args.getOrNull(1) ?: return@setExecutor false

                val to = args.drop(2).mapNotNull { Material.matchMaterial(it)?.createBlockData() }
                if (to.isEmpty()) return@setExecutor true

                var pieces = getPieces(mode)

                if (from.lowercase() == "cloak") {
                    pieces = pieces.filter { it.tags.contains("cloak") }
                } else {
                    val material = Material.matchMaterial(from) ?: return@setExecutor true
                    pieces = pieces.filter { it.block.material == material }
                }

                pieces.forEach { piece ->
                    piece.block = to.random()
                }

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                if (args.size == 1) {
                    return@setTabCompleter listOf("body", "legs", "all").filter { it.contains(args.last(), true) }
                }

                val extra = if (args.size == 2) listOf("cloak") else emptyList()
                val materials = extra + Material.entries
                    .filter { it.isBlock }
                    .map { it.key.toString() }
                return@setTabCompleter materials.filter { it.contains(args.last(), true) }
            }
        }

        getCommand("torso_model")?.apply {
            setExecutor { _, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false

                val model = SpiderTorsoModels.entries.find { it.name.equals(option, true) }?.model?.clone() ?: return@setExecutor false

                val currentScale = AppState.options.bodyPlan.scale.toFloat()
                AppState.options.bodyPlan.bodyModel = model.scale(currentScale)

                val cloakBlock = AppState.options.bodyPlan.bodyModel.pieces.find {
                    it.tags.contains("cloak")
                }

                if (cloakBlock != null) AppState.options.bodyPlan.legs.forEach { leg ->
                    leg.segments.forEach { segment ->
                        segment.model.pieces.forEach { piece ->
                            if (piece.tags.contains("cloak")) {
                                piece.block = cloakBlock.block
                            }
                        }
                    }
                }

                saveConfig()

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                return@setTabCompleter SpiderTorsoModels
                    .entries.map { it.name.lowercase() }
                    .filter { it.contains(args.last(), true) }
            }
        }

        getCommand("fall")?.setExecutor { sender, _, _, args ->
            val spider = AppState.spider ?: return@setExecutor true

            val height = args[0].toDoubleOrNull()

            if (height == null) {
                sender.sendMessage("Usage: /spider:fall <height>")
                return@setExecutor true
            }

            spider.teleport(spider.location().add(0.0, height, 0.0))


            return@setExecutor true
        }

        getCommand("body_plan")?.apply {
            val bodyPlanTypes = mapOf(
                "biped" to ::bipedBodyPlan,
                "quadruped" to ::quadrupedBodyPlan,
                "hexapod" to ::hexapodBodyPlan,
                "octopod" to ::octopodBodyPlan,
                "quadbot" to ::quadBotBodyPlan,
                "hexbot" to ::hexBotBodyPlan,
                "octobot" to ::octoBotBodyPlan,
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

                val oldScale = AppState.options.bodyPlan.scale
                AppState.options.scale(scale / oldScale)
                AppState.options.bodyPlan = bodyPlan(segmentCount, segmentLength).apply { scale(scale) }

                saveConfig()

                // recreate spider
                val spider = AppState.spider
                if (spider != null) {
                    AppState.spider = AppState.createSpider(spider.location())
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

            val inventory = createInventory(null, 9 * 3, "Items")
            for (item in CustomItemRegistry.items) {
                inventory.addItem(item.defaultItem.clone())
            }

            player.openInventory(inventory)

            return@setExecutor true
        }
    }
}