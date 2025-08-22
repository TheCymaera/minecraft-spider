package com.heledron.spideranimation

import com.google.gson.Gson
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.heledron.spideranimation.spider.configuration.CloakOptions
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.spider.configuration.SpiderDebugOptions
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.misc.splay
import com.heledron.spideranimation.spider.presets.*
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.CustomItemRegistry
import com.heledron.spideranimation.utilities.Serializer
import com.heledron.spideranimation.utilities.runLater
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit.createInventory
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.data.BlockData
import org.bukkit.command.BlockCommandSender
import org.bukkit.entity.Display
import org.bukkit.entity.Player

fun registerCommands(plugin: SpiderAnimationPlugin, commands: Commands) {

    // Helper function to simplify registration
    fun register(command: LiteralArgumentBuilder<CommandSourceStack>, description: String) {
        commands.register(command.build(), description)
    }

    // Command: /items
    val itemsCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("items")
        .requires { it.sender.hasPermission("spider-animation.items") }
        .executes {
            val player = it.source.sender as? Player ?: return@executes 0
            val inventory = createInventory(null, 9 * 3, Component.text("Items"))
            CustomItemRegistry.items.forEach { item -> inventory.addItem(item.defaultItem.clone()) }
            player.openInventory(inventory)
            1
        }
    register(itemsCommand, "Open the items menu")

    // Command: /fall <height>
    val fallCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("fall")
        .requires { it.sender.hasPermission("spider-animation.fall") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, Double>("height", DoubleArgumentType.doubleArg())
            .executes {
                val spider = AppState.spider ?: return@executes 1
                val height = DoubleArgumentType.getDouble(it, "height")
                spider.teleport(spider.location().add(0.0, height, 0.0))
                1
            }
        )
    register(fallCommand, "Teleport the spider up by the specified height")

    // Command: /scale <scale>
    val scaleCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("scale")
        .requires { it.sender.hasPermission("spider-animation.scale") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, Double>("scale", DoubleArgumentType.doubleArg())
            .executes {
                val scale = DoubleArgumentType.getDouble(it, "scale")
                val oldScale = AppState.options.bodyPlan.scale
                AppState.options.scale(scale / oldScale)
                plugin.writeAndSaveConfig()
                AppState.recreateSpider()
                it.source.sender.sendMessage(Component.text("Set scale to $scale"))
                1
            }
        )
    register(scaleCommand, "Scale the spider")

    // Command: /splay [delay]
    val splayCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("splay")
        .requires { it.sender.hasPermission("spider-animation.splay") }
        .executes {
            splay()
            1
        }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, Long>("delay", LongArgumentType.longArg(0))
            .executes {
                val delay = LongArgumentType.getLong(it, "delay")
                runLater(delay) { splay() }
                1
            }
        )
    register(splayCommand, "Splay the spider's legs")

    // Command: /torso_model <model>
    val torsoModelCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("torso_model")
        .requires { it.sender.hasPermission("spider-animation.torso_model") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("model", StringArgumentType.string())
            .suggests { _, builder ->
                SpiderTorsoModels.entries.map { it.name.lowercase() }.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes {
                val option = StringArgumentType.getString(it, "model")
                val model = SpiderTorsoModels.entries.find { e -> e.name.equals(option, true) }?.model?.clone()
                if (model == null) {
                    it.source.sender.sendMessage(Component.text("Invalid torso model: $option"))
                    return@executes 0
                }
                val currentScale = AppState.options.bodyPlan.scale.toFloat()
                AppState.options.bodyPlan.bodyModel = model.scale(currentScale)
                plugin.writeAndSaveConfig()
                1
            }
        )
    register(torsoModelCommand, "Set the torso model of the spider")

    // Command: /leg_model <model> [material]
    val legModelCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("leg_model")
        .requires { it.sender.hasPermission("spider-animation.leg_model") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("model", StringArgumentType.string())
            .suggests { _, builder ->
                listOf("empty", "mechanical", "line").forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes {
                val sender = it.source.sender
                val option = StringArgumentType.getString(it, "model")
                when (option) {
                    "empty" -> applyEmptyLegModel(AppState.options.bodyPlan)
                    "mechanical" -> applyMechanicalLegModel(AppState.options.bodyPlan)
                    else -> {
                        sender.sendMessage(Component.text("Invalid leg model: $option, or missing arguments for 'line'"))
                        return@executes 0
                    }
                }
                plugin.writeAndSaveConfig()
                sender.sendMessage(Component.text("Set leg model to $option"))
                1
            }
            .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("material", StringArgumentType.string())
                .suggests { _, builder ->
                    Material.entries.filter { m -> m.isBlock }.map { m -> m.key.toString() }.forEach(builder::suggest)
                    builder.buildFuture()
                }
                .executes {
                    val sender = it.source.sender
                    val option = StringArgumentType.getString(it, "model")
                    if (option != "line") {
                        sender.sendMessage(Component.text("Only the 'line' model takes a material argument."))
                        return@executes 0
                    }
                    val materialName = StringArgumentType.getString(it, "material")
                    val material = Material.matchMaterial(materialName)?.createBlockData()
                    if (material == null) {
                        sender.sendMessage(Component.text("Invalid material: $materialName"))
                        return@executes 0
                    }
                    applyLineLegModel(AppState.options.bodyPlan, material)
                    plugin.writeAndSaveConfig()
                    sender.sendMessage(Component.text("Set leg model to $option with material $materialName"))
                    1
                }
            )
        )
    register(legModelCommand, "Set the leg model of the spider")

    // Command: /preset <name> [segmentCount] [segmentLength]
    val presets = mapOf(
        "biped" to ::biped, "quadruped" to ::quadruped, "hexapod" to ::hexapod,
        "octopod" to ::octopod, "quadbot" to ::quadBot, "hexbot" to ::hexBot, "octobot" to ::octoBot
    )
    val presetCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("preset")
        .requires { it.sender.hasPermission("spider-animation.preset") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", StringArgumentType.string())
            .suggests { _, b -> presets.keys.forEach(b::suggest); b.buildFuture() }
            .executes {
                val name = StringArgumentType.getString(it, "name")
                val createPreset = presets[name] ?: return@executes 0
                val segmentCount = if (name.contains("bot")) 4 else 3

                AppState.options = createPreset(segmentCount, 1.0)
                plugin.writeAndSaveConfig()
                AppState.recreateSpider()
                it.source.sender.sendMessage(Component.text("Applied preset: $name"))
                1
            }
            .then(RequiredArgumentBuilder.argument<CommandSourceStack, Int>("segmentCount", IntegerArgumentType.integer())
                .executes {
                    val name = StringArgumentType.getString(it, "name")
                    val createPreset = presets[name] ?: return@executes 0
                    val segmentCount = IntegerArgumentType.getInteger(it, "segmentCount")

                    AppState.options = createPreset(segmentCount, 1.0)
                    plugin.writeAndSaveConfig()
                    AppState.recreateSpider()
                    it.source.sender.sendMessage(Component.text("Applied preset: $name"))
                    1
                }
                .then(RequiredArgumentBuilder.argument<CommandSourceStack, Double>("segmentLength", DoubleArgumentType.doubleArg())
                    .executes {
                        val name = StringArgumentType.getString(it, "name")
                        val createPreset = presets[name] ?: return@executes 0
                        val segmentCount = IntegerArgumentType.getInteger(it, "segmentCount")
                        val segmentLength = DoubleArgumentType.getDouble(it, "segmentLength")

                        AppState.options = createPreset(segmentCount, segmentLength)
                        plugin.writeAndSaveConfig()
                        AppState.recreateSpider()
                        it.source.sender.sendMessage(Component.text("Applied preset: $name"))
                        1
                    }
                )
            )
        )
    register(presetCommand, "Apply a preset")

    // Command: /options <category> <option> [value]
    val optionsCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("options")
        .requires { it.sender.hasPermission("spider-animation.options") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("arguments", StringArgumentType.greedyString())
            .executes {
                val sender = it.source.sender
                val args = StringArgumentType.getString(it, "arguments").split(" ").toTypedArray()

                val options = mapOf(
                    "walkGait" to { AppState.options.walkGait }, "gallopGait" to { AppState.options.gallopGait },
                    "debug" to { AppState.options.debug }, "misc" to { AppState.miscOptions }, "cloak" to { AppState.options.cloak },
                )
                val defaultObjects = mapOf(
                    "walkGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.walkGait },
                    "gallopGait" to { SpiderOptions().apply { scale(AppState.options.bodyPlan.scale) }.gallopGait },
                    "debug" to { SpiderDebugOptions() }, "misc" to { MiscellaneousOptions() }, "cloak" to { CloakOptions() },
                )

                val obj = options[args.getOrNull(0)]?.invoke()
                if (obj == null) {
                    sender.sendMessage(Component.text("Invalid category. Available: ${options.keys.joinToString()}"))
                    return@executes 0
                }
                val default = defaultObjects[args.getOrNull(0)]?.invoke() ?: return@executes 0
                val option = args.getOrNull(1) ?: return@executes 0
                val valueUnParsed = args.getOrNull(2)

                fun printable(o: Any?) = Gson().toJson(o)

                if (option == "reset") {
                    val map = Serializer.toMap(default) as Map<*, *>
                    Serializer.writeFromMap(obj, map)
                    sender.sendMessage(Component.text("Reset all options"))
                } else if (valueUnParsed == null) {
                    val value = Serializer.get(obj, option)
                    sender.sendMessage(Component.text("Option $option is ${printable(value)}"))
                } else if (valueUnParsed == "reset") {
                    val value = Serializer.get(default, option)
                    Serializer.set(obj, option, value)
                    sender.sendMessage(Component.text("Reset option $option to ${printable(value)}"))
                } else {
                    val parsed = try {
                        Gson().fromJson(valueUnParsed, Any::class.java)
                    } catch (_: Exception) {
                        sender.sendMessage(Component.text("Could not parse: $valueUnParsed"))
                        return@executes 1
                    }
                    Serializer.setMap(obj, option, parsed)
                    val value = Serializer.get(obj, option)
                    sender.sendMessage(Component.text("Set option $option to ${printable(value)}"))
                }
                plugin.writeAndSaveConfig()
                1
            }
        )
    register(optionsCommand, "Set gait options")

    // Command: /set_sound <kind> <sound> [volume] [pitch] [volumeVary] [pitchVary]
    val setSoundExecutor: (CommandContext<CommandSourceStack>) -> Int = {
        val sender = it.source.sender
        val kind = StringArgumentType.getString(it, "kind")
        val soundString = StringArgumentType.getString(it, "sound")
        val soundID = NamespacedKey.fromString(soundString)
        val sound = if (soundID != null) Registry.SOUNDS.get(soundID) else null

        // First guard clause for invalid sound
        if (sound == null) {
            sender.sendMessage(Component.text("Invalid sound: $soundString"))
            0 // Return 0 for failure
        }
        // Second guard clause for invalid kind
        else if (kind != "step") {
            sender.sendMessage(Component.text("Invalid sound kind: $kind"))
            0 // Return 0 for failure
        }
        // Success path
        else {
            val volume = it.getArgumentOrNull("volume", Float::class.java) ?: 1.0f
            val pitch = it.getArgumentOrNull("pitch", Float::class.java) ?: 1.0f
            val volumeVary = it.getArgumentOrNull("volumeVary", Float::class.java) ?: 0.1f
            val pitchVary = it.getArgumentOrNull("pitchVary", Float::class.java) ?: 0.1f

            val soundPlayer = SoundPlayer(sound, volume, pitch, volumeVary, pitchVary)
            AppState.options.sound.step = soundPlayer

            sender.sendMessage(Component.text("Set $kind sound to $soundString"))
            1 // Return 1 for success
        }
    }

// Command: /spider modify_model <...query>
    val modifyModelCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("modify_model")
        .requires { it.sender.hasPermission("spider-animation.modify_model") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("query", StringArgumentType.greedyString())
            .suggests { _, builder ->
                val args = builder.remaining.ifEmpty { " " }.split(" ").toTypedArray()

                fun getLegPieces() = AppState.options.bodyPlan.legs.flatMap { it.segments }.flatMap { it.model.pieces }
                fun getBodyPieces() = AppState.options.bodyPlan.bodyModel.pieces
                fun getAllPieces() = getLegPieces() + getBodyPieces()
                fun getAvailableTags() = getAllPieces().flatMap { it.tags }.distinct()

                val clauses = listOf("or", "set_block", "brightness", "scale", "copy_block", "random")
                val currentClauseIndex = args.indexOfLast { it in clauses }
                val currentClauseLength = if (currentClauseIndex == -1) args.size else args.size - currentClauseIndex - 1
                val currentClause = if (currentClauseIndex == -1) "or" else args[currentClauseIndex]

                val tags = getAvailableTags()

                val materials = Registry.BLOCK.mapNotNull { Registry.BLOCK.getKey(it)?.toString() }

                val options = mutableListOf<String>()

                if (currentClause == "or") {
                    if (currentClauseLength >= 1) options += clauses
                    options += tags + materials
                } else if (currentClause == "set_block") {
                    if (currentClauseLength >= 1) options += clauses
                    options += materials
                } else if (currentClause == "brightness") {
                    options += if (currentClauseLength < 3) (0..15).map { it.toString() } else clauses
                } else if (currentClause == "scale") {
                    if (currentClauseLength >= 3) options += clauses
                } else if (currentClause == "copy_block") {
                    options += if (currentClauseLength < 4) listOf("~") else clauses
                }

                options.filter { it.startsWith(args.last(), true) }.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes {
                val sender = it.source.sender
                val query = StringArgumentType.getString(it, "query")
                val args = query.split(" ").toTypedArray()

                val changes = mutableListOf<(piece: BlockDisplayModelPiece) -> Unit>()
                fun getLegPieces() = AppState.options.bodyPlan.legs.flatMap { it.segments }.flatMap { it.model.pieces }
                fun getBodyPieces() = AppState.options.bodyPlan.bodyModel.pieces
                fun getAllPieces() = getLegPieces() + getBodyPieces()
                fun getAvailableTags() = getAllPieces().flatMap { it.tags }.distinct()
                val availableTags = getAvailableTags()
                val orGroups = mutableListOf<MutableList<(piece: BlockDisplayModelPiece) -> Boolean>>()

                var clause = "or"
                orGroups.add(mutableListOf())
                var i = 0
                while (i < args.size) {
                    val arg = args[i]
                    if (arg == "or") { clause = "or"; orGroups.add(mutableListOf()); i++; continue }
                    if (arg == "set_block") {
                        clause = "set_block"
                        val palette = mutableListOf<BlockData>()
                        var offset = 1
                        while (true) {
                            val blockID = args.getOrNull(i + offset) ?: break
                            val block = Material.matchMaterial(blockID)?.createBlockData() ?: break
                            palette.add(block)
                            offset++
                        }
                        changes.add { piece -> piece.block = palette.random() }
                        i += offset; continue
                    }
                    if (arg == "copy_block") {
                        clause = "copy_block"
                        val location = (sender as? Player)?.location ?: (sender as? BlockCommandSender)?.block?.location
                        if (location == null) {
                            sender.sendMessage(Component.text("Cannot copy block without location"))
                            return@executes 1
                        }
                        fun resolveCoord(string: String, base: Double) = if (string.startsWith("~")) base + (string.substring(1).toDoubleOrNull() ?: 0.0) else string.toDoubleOrNull() ?: base
                        val x = resolveCoord(args.getOrNull(i + 1) ?: "~", location.x)
                        val y = resolveCoord(args.getOrNull(i + 2) ?: "~", location.y)
                        val z = resolveCoord(args.getOrNull(i + 3) ?: "~", location.z)
                        val block = location.world.getBlockAt(x.toInt(), y.toInt(), z.toInt()).blockData
                        changes.add { piece -> piece.block = block }
                        i += 4; continue
                    }
                    if (arg == "brightness") {
                        clause = "brightness"
                        val blockLight = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                        val skyLight = args.getOrNull(i + 2)?.toIntOrNull() ?: 15
                        changes.add { piece -> piece.brightness = Display.Brightness(blockLight, skyLight) }
                        i += 3; continue
                    }
                    if (arg == "scale") {
                        clause = "scale"
                        val x = args.getOrNull(i + 1)?.toFloatOrNull() ?: 1.0f
                        val y = args.getOrNull(i + 2)?.toFloatOrNull() ?: 1.0f
                        val z = args.getOrNull(i + 3)?.toFloatOrNull() ?: 1.0f
                        changes.add { piece -> piece.scale(x, y, z) }
                        i += 4; continue
                    }
                    if (clause == "or") {
                        val material = Material.matchMaterial(arg)
                        if (material != null) orGroups.last().add { piece -> piece.block.material == material }
                        else if (arg in availableTags) orGroups.last().add { piece -> arg in piece.tags }
                        else if (arg == "random") {
                            val chance = args.getOrNull(i + 1)?.toDoubleOrNull()
                            if (chance == null) {
                                sender.sendMessage(Component.text("Missing chance for random condition"))
                                return@executes 1
                            }
                            orGroups.last().add { Math.random() < chance }
                            i++ // consume extra arg
                        }
                    }
                    i++
                }
                val finalOrGroups = orGroups.filter { it.isNotEmpty() }
                val pieces = getAllPieces().filter { piece -> finalOrGroups.isEmpty() || finalOrGroups.any { andGroup -> andGroup.all { it(piece) } } }
                pieces.forEach { piece -> changes.forEach { it(piece) } }
                sender.sendMessage(Component.text("Modified ${pieces.size} blocks with ${changes.size} changes"))
                1
            }
        )
    register(modifyModelCommand, "Modify the spider's model")

// Command: /set_sound <kind> <sound> [volume] [pitch] [volumeVary] [pitchVary]
    val setSoundCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("set_sound")
        .requires { it.sender.hasPermission("spider-animation.set_sound") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("kind", StringArgumentType.string())
            .suggests { _, b -> b.suggest("step").buildFuture() }
            .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("sound", StringArgumentType.string())
                .suggests { _, b ->
                    Registry.SOUNDS.forEach { sound ->
                        // Ask the registry for the key and only suggest it if it's not null
                        Registry.SOUNDS.getKey(sound)?.toString()?.let(b::suggest)
                    }
                    b.buildFuture()
                }
                .executes(setSoundExecutor)
                .then(RequiredArgumentBuilder.argument<CommandSourceStack, Float>("volume", FloatArgumentType.floatArg())
                    .executes(setSoundExecutor)
                    .then(RequiredArgumentBuilder.argument<CommandSourceStack, Float>("pitch", FloatArgumentType.floatArg())
                        .executes(setSoundExecutor)
                        .then(RequiredArgumentBuilder.argument<CommandSourceStack, Float>("volumeVary", FloatArgumentType.floatArg())
                            .executes(setSoundExecutor)
                            .then(RequiredArgumentBuilder.argument<CommandSourceStack, Float>("pitchVary", FloatArgumentType.floatArg())
                                .executes(setSoundExecutor)
                            )
                        )
                    )
                )
            )
        )
    register(setSoundCommand, "Set the sound of the spider")

// Command: /animated_palette <...args>
    val animatedPaletteCommand = LiteralArgumentBuilder.literal<CommandSourceStack>("animated_palette")
        .requires { it.sender.hasPermission("spider-animation.animated_palette") }
        .then(RequiredArgumentBuilder.argument<CommandSourceStack, String>("arguments", StringArgumentType.greedyString())
            .suggests { _, builder ->
                val args = builder.remaining.ifEmpty { " " }.split(" ").toTypedArray()
                val options = mutableListOf<String>()
                val presets = AnimatedPalettes.entries.map { it.name.lowercase() }
                if (args.size == 1) options += listOf("eye", "blinking_lights")
                if (args.size == 2) options += presets + "custom"
                if (args.size > 2 && args[1] == "custom") {
                    val materials = Material.entries.filter { it.isBlock }.map { it.key.toString() }
                    val brightness = (0..15).map { it.toString() }
                    when ((args.size - 3) % 3) {
                        0 -> options += materials
                        1, 2 -> options += brightness
                    }
                }
                options.filter { it.startsWith(args.last(), true) }.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes {
                val sender = it.source.sender
                val args = StringArgumentType.getString(it, "arguments").split(" ").toTypedArray()

                val target = args.getOrNull(0)
                if (target == null) {
                    sender.sendMessage(Component.text("Usage: /animated_palette <eye|blinking_lights> <preset|custom> [blocks...]"))
                    return@executes 0
                }

                val presetName = args.getOrNull(1)

                // **FIX 1:** Changed variable type from Brightness? to Brightness
                val palette: MutableList<Pair<BlockData, Display.Brightness>> = presetName?.let { name ->
                    AnimatedPalettes.entries.find { p -> p.name.equals(name, true) }?.palette
                        // **FIX 2:** Use mapNotNull to safely filter and convert the list to a non-nullable type
                        ?.map { (block, brightness) -> brightness.let { b -> block to b } }
                        ?.toMutableList()
                } ?: mutableListOf()


                if (presetName == "custom" || (presetName != null && palette.isEmpty())) {
                    var i = 2
                    while (i < args.size) {
                        val blockID = args.getOrNull(i) ?: break
                        val block = Material.matchMaterial(blockID)?.createBlockData()
                        if (block == null) {
                            sender.sendMessage(Component.text("Invalid material: $blockID"))
                            return@executes 1
                        }
                        val blockLight = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                        val skyLight = args.getOrNull(i + 2)?.toIntOrNull() ?: 15
                        palette.add(block to Display.Brightness(blockLight, skyLight))
                        i += 3
                    }
                }

                when (target) {
                    "eye" -> AppState.options.bodyPlan.eyePalette = palette
                    "blinking_lights" -> AppState.options.bodyPlan.blinkingPalette = palette
                    else -> {
                        sender.sendMessage(Component.text("Invalid target. Use 'eye' or 'blinking_lights'."))
                        return@executes 0
                    }
                }
                sender.sendMessage(Component.text("Set $target palette to ${palette.size} blocks"))
                1
            }
        )
    register(animatedPaletteCommand, "Modify animated palette")
}


// Helper to get optional arguments from context
fun <T> CommandContext<CommandSourceStack>.getArgumentOrNull(name: String, clazz: Class<T>): T? {
    return try { this.getArgument(name, clazz) } catch (e: IllegalArgumentException) { null }
}