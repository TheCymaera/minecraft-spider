package com.heledron.spideranimation

import com.google.common.collect.Lists
import com.google.gson.Gson
import com.heledron.spideranimation.spider.configuration.CloakOptions
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.spider.configuration.SpiderDebugOptions
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.*
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.CustomItemRegistry
import com.heledron.spideranimation.utilities.Serializer
import org.bukkit.Bukkit.createInventory
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.entity.Display.Brightness

fun registerCommands(plugin: SpiderAnimationPlugin) {
    fun getCommand(name: String) = plugin.getCommand(name) ?: throw Exception("Command $name not found")

    getCommand("options").apply {
        val options = mapOf(
            "walkGait" to { AppState.options.walkGait },
            "gallopGait" to { AppState.options.gallopGait },

            "debug" to { AppState.options.debug },
            "misc" to { AppState.miscOptions },
            "cloak" to { AppState.options.cloak },
        )

        val defaultObjects = mapOf(
            "walkGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.walkGait },
            "gallopGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.gallopGait },

            "debug" to { SpiderDebugOptions() },
            "misc" to { MiscellaneousOptions() },
            "cloak" to { CloakOptions() },
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

            plugin.writeAndSaveConfig()

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

    getCommand("modify_model").apply {
        fun getLegPieces() = AppState.options.bodyPlan.legs.flatMap { it.segments }.flatMap { it.model.pieces }
        fun getBodyPieces() = AppState.options.bodyPlan.bodyModel.pieces
        fun getAllPieces() = getLegPieces() + getBodyPieces()
        fun getAvailableTags() = getAllPieces().flatMap { it.tags }.distinct()

        setExecutor { sender, _, _, args ->
            val changes = mutableListOf<(piece: BlockDisplayModelPiece) -> Unit>()

            val availableTags = getAvailableTags()
            val orGroups = mutableListOf<MutableList<(piece: BlockDisplayModelPiece)->Boolean>>()

            var clause = "or"
            orGroups.addLast(mutableListOf())
            for ((index,arg) in args.withIndex()) {
                if (arg == "or") {
                    clause = "or"
                    orGroups.addLast(mutableListOf())
                    continue
                }

                if (arg == "set_block") {
                    clause = "set_block"

                    val palette = mutableListOf<BlockData>()
                    while (true) {
                        val blockID = args.getOrNull(index + palette.size + 1) ?: break
                        val block = Material.matchMaterial(blockID)?.createBlockData() ?: break
                        palette.add(block)
                    }

                    changes.add { piece -> piece.block = palette.random() }
                    continue
                }

                if (arg == "brightness") {
                    clause = "brightness"
                    val blockLight = args.getOrNull(index + 1)?.toIntOrNull() ?: 0
                    val skyLight = args.getOrNull(index + 2)?.toIntOrNull() ?: 15
                    val brightness = Display.Brightness(blockLight, skyLight)
                    changes.add { piece -> piece.brightness = brightness }
                    continue
                }

                if (arg == "scale") {
                    clause = "scale"
                    val x = args.getOrNull(index + 1)?.toFloatOrNull() ?: 1.0f
                    val y = args.getOrNull(index + 2)?.toFloatOrNull() ?: 1.0f
                    val z = args.getOrNull(index + 3)?.toFloatOrNull() ?: 1.0f
                    changes.add { piece -> piece.scale(x, y, z) }
                    continue
                }

                if (clause == "or") {
                    val material = Material.matchMaterial(arg)
                    if (material != null) {
                        orGroups.last().add { piece -> piece.block.material.key.toString() == arg }
                        continue
                    }

                    if (arg in availableTags) {
                        orGroups.last().add { piece -> piece.tags.contains(arg) }
                        continue
                    }

                    if (arg == "random") {
                        val chance = args.getOrNull(index + 1)?.toDoubleOrNull() ?: run {
                            sender.sendMessage("Missing chance for random condition")
                            return@setExecutor true
                        }
                        orGroups.last().add { _ -> Math.random() < chance }
                    }
                }
            }

            val pieces = getAllPieces().filter { piece -> orGroups.any { andGroup -> andGroup.all { it(piece) } } }

            pieces.forEach { piece -> changes.forEach { it(piece) } }

            sender.sendMessage("Modified ${pieces.size} blocks with ${changes.size} changes")

            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            val clauses = listOf("or", "set_block", "brightness")
            val currentClauseIndex = args.indexOfLast { it in clauses }
            val currentClauseLength = if (currentClauseIndex == -1) args.size else args.size - currentClauseIndex - 1
            val currentClause = if (currentClauseIndex == -1) "or" else args[currentClauseIndex]

            val tags = getAvailableTags()
            val materials = Material.entries.filter { it.isBlock }.map { it.key.toString() }

            val options = mutableListOf<String>()

            if (currentClause == "or") {
                if (currentClauseLength > 1) options += clauses
                options += tags + materials
            }
            if (currentClause == "set_block") {
                if (currentClauseLength > 1) options += clauses
                options += materials
            }
            if (currentClause == "brightness") {
                options += if (currentClauseLength > 2)
                    clauses else List(16) { it.toString() }
            }
            if (currentClause == "scale") {
                if (currentClauseLength > 3) options += clauses
            }

            return@setTabCompleter options.filter { it.contains(args.last(), true) }
        }
    }

    getCommand("animated_palette").apply {
        setExecutor { sender, _, _, args ->
            val target = args.getOrNull(0) ?: return@setExecutor false
            val preset = args.getOrNull(1)?.let {
                AnimatedPalettes.entries.find { match -> match.name.equals(it, true) }
            }?.palette?.toMutableList()

            val palette = preset ?: mutableListOf()


            for (i in 2 until args.size step 3) {
                val blockID = args[i]
                val block = Material.matchMaterial(blockID)?.createBlockData() ?: run {
                    sender.sendMessage("Invalid material: $blockID")
                    return@setExecutor false
                }

                val blockLight = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                val skyLight = args.getOrNull(i + 2)?.toIntOrNull() ?: 15
                val brightness = Brightness(blockLight, skyLight)

                palette.add(block to brightness)
            }

            when (target) {
                "eye" -> AppState.options.bodyPlan.eyePalette = palette
                "blinking_lights" -> AppState.options.bodyPlan.blinkingPalette = palette
                else -> return@setExecutor false
            }

            sender.sendMessage("Set $target palette to ${palette.size} blocks")


            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            val options = mutableListOf<String>()
            val presets = AnimatedPalettes.entries.map { it.name.lowercase() }

            if (args.size == 1) options += listOf("eye", "blinking_lights")

            if (args.size == 2) options += presets + "custom"

            if (args.size > 1 && args.getOrNull(1) == "custom") {
                val materials = Material.entries.filter { it.isBlock }.map { it.key.toString() }
                val brightness = List(16) { it.toString() }
                val size = args.size - 3
                if (size % 3 == 0) options += materials
                if (size % 3 == 1) options += brightness
                if (size % 3 == 2) options += brightness
            }

            return@setTabCompleter options.filter { it.contains(args.last(), true) }
        }
    }

    getCommand("torso_model").apply {
        setExecutor { _, _, _, args ->
            val option = args.getOrNull(0) ?: return@setExecutor false

            val model = SpiderTorsoModels.entries.find { it.name.equals(option, true) }?.model?.clone() ?: return@setExecutor false

            val currentScale = AppState.options.bodyPlan.scale.toFloat()
            AppState.options.bodyPlan.bodyModel = model.scale(currentScale)
            plugin.writeAndSaveConfig()

            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            return@setTabCompleter SpiderTorsoModels
                .entries.map { it.name.lowercase() }
                .filter { it.contains(args.last(), true) }
        }
    }

    getCommand("leg_model").apply {
        setExecutor { sender, _, _, args ->
            val option = args.getOrNull(0) ?: return@setExecutor false

            when (option) {
                "empty" -> applyEmptyLegModel(AppState.options.bodyPlan)
                "mechanical" -> applyMechanicalLegModel(AppState.options.bodyPlan)
                "line" -> {
                    val materialName = args.getOrNull(1) ?: "minecraft:netherite_block"
                    val material = Material.matchMaterial(materialName)?.createBlockData() ?: run {
                        sender.sendMessage("Invalid material: $materialName")
                        return@setExecutor true
                    }

                    applyLineLegModel(AppState.options.bodyPlan, material)
                }
                else -> {
                    sender.sendMessage("Invalid leg model: $option")
                    return@setExecutor true
                }
            }

            plugin.writeAndSaveConfig()

            sender.sendMessage("Set leg model to $option")

            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            var options = listOf<String>()

            if (args.size == 1) options = listOf("empty", "mechanical", "line")
            if (args.getOrNull(0) == "line") options = Material.entries.map { it.key.toString() }

            return@setTabCompleter options.filter { it.contains(args.last(), true) }
        }
    }

    getCommand("fall").setExecutor { sender, _, _, args ->
        val spider = AppState.spider ?: return@setExecutor true

        val height = args[0].toDoubleOrNull()

        if (height == null) {
            sender.sendMessage("Usage: /spider:fall <height>")
            return@setExecutor true
        }

        spider.teleport(spider.location().add(0.0, height, 0.0))


        return@setExecutor true
    }

    getCommand("preset").apply {
        val presets = mapOf(
            "biped" to ::biped,
            "quadruped" to ::quadruped,
            "hexapod" to ::hexapod,
            "octopod" to ::octopod,
            "quadbot" to ::quadBot,
            "hexbot" to ::hexBot,
            "octobot" to ::octoBot,
        )

        setExecutor { sender, _, _, args ->
            val name = args.getOrNull(0) ?: return@setExecutor false

            val segmentCount = args.getOrNull(1)?.toIntOrNull() ?: 4
            val segmentLength = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0

            val createPreset = presets[name]
            if (createPreset == null) {
                sender.sendMessage("Invalid preset: $name")
                return@setExecutor true
            }

            AppState.options = createPreset(segmentCount, segmentLength)
            plugin.writeAndSaveConfig()

            AppState.recreateSpider()


            sender.sendMessage("Applied preset: $name")

            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            if (args.size == 1) {
                return@setTabCompleter presets.keys.filter { it.contains(args.last(), true) }
            }
            return@setTabCompleter emptyList()
        }
    }

    getCommand("scale").setExecutor { sender, _, _, args ->
        val scale = args[0].toDoubleOrNull()

        if (scale == null) {
            sender.sendMessage("Usage: /spider:scale <scale>")
            return@setExecutor true
        }

        val oldScale = AppState.options.bodyPlan.scale
        AppState.options.scale(scale / oldScale)

        plugin.writeAndSaveConfig()

        AppState.recreateSpider()

        sender.sendMessage("Set scale to $scale")

        return@setExecutor true
    }

    getCommand("items").setExecutor { sender, _, _, _ ->
        val player = sender as? org.bukkit.entity.Player ?: return@setExecutor true

        val inventory = createInventory(null, 9 * 3, "Items")
        for (item in CustomItemRegistry.items) {
            inventory.addItem(item.defaultItem.clone())
        }

        player.openInventory(inventory)

        return@setExecutor true
    }

    getCommand("set_sound").apply {
        setExecutor() { sender, _, _, args ->
            val kind = args.getOrNull(0) ?: return@setExecutor false
            val soundString = args.getOrNull(1) ?: return@setExecutor false
            val soundID = NamespacedKey.fromString(soundString) ?: return@setExecutor false
            val sound = Registry.SOUNDS.get(soundID) ?: return@setExecutor false

            val volume = args.getOrNull(2)?.toFloatOrNull() ?: 1.0f
            val pitch = args.getOrNull(3)?.toFloatOrNull() ?: 1.0f
            val volumeVary = args.getOrNull(4)?.toFloatOrNull() ?: 0.1f
            val pitchVary = args.getOrNull(5)?.toFloatOrNull() ?: 0.1f

            val soundPlayer = SoundPlayer(
                sound = sound,
                volume = volume,
                pitch = pitch,
                volumeVary = volumeVary,
                pitchVary = pitchVary
            )


            when (kind) {
                "step" -> AppState.options.sound.step = soundPlayer
                else -> return@setExecutor false
            }

            sender.sendMessage("Set $kind to $soundString")

            return@setExecutor true
        }

        setTabCompleter { _, _, _, args ->
            val options = mutableListOf<String>()

            if (args.size == 1) options += "step"
            if (args.size == 2) options += Lists.newArrayList<Sound>(Registry.SOUNDS).map { it.key.toString() }

            return@setTabCompleter options.filter { it.contains(args.last(), true) }
        }
    }
}