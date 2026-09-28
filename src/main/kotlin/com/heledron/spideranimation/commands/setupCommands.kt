package com.heledron.spideranimation.commands

import com.google.gson.JsonSyntaxException
import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.MiscellaneousOptions
import com.heledron.spideranimation.SpiderAnimationPlugin
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.splay
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.*
import com.heledron.spideranimation.utilities.Serializer
import com.heledron.spideranimation.utilities.custom_items.openCustomItemInventory
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.events.runLater
import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

private const val PERMISSION = "spider-animation.command"
private const val SUCCESS = 1

fun setupCommands(plugin: SpiderAnimationPlugin) {
    plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
        event.registrar().register(spiderCommandTree { plugin.writeAndSaveConfig() }.build(), "Spider animation")
    }
}

fun spiderCommandTree(afterChange: () -> Unit): LiteralArgumentBuilder<CommandSourceStack> =
    Commands.literal("spider")
        .requires { it.sender.hasPermission(PERMISSION) }
        .executes { ctx ->
            ctx.source.sender.sendPlainMessage("Usage: /spider <options|preset|scale|splay|items>")
            SUCCESS
        }
        .then(optionsCommand(afterChange))
        .then(presetCommand())
        .then(scaleCommand(afterChange))
        .then(splayCommand())
        .then(itemsCommand())

private fun optionsCommand(afterChange: () -> Unit): LiteralArgumentBuilder<CommandSourceStack> {
    // KNOWN LIMITATION: A path containing `[` must be quoted.
    // Paper only accepts CustomArgumentType if its native type is one of the six Brigadier primitives.
    // A greedy string would make the client treat the rest of the line as part of the path and
    // prevent auto-completion for subsequent arguments.
    // Replace with NBTPathArgument once Paper supports it, or if porting to a mod.
    fun pathArgument() =
        Commands.argument("path", StringArgumentType.string()).suggests(::suggestOptionPaths)

    val groupArgument = Commands.argument("group", StringArgumentType.word())
        .suggests { _, builder -> suggest(builder, OPTION_GROUPS.keys) }
        .then(
            pathArgument()
                .executes(::getOption)
                .then(
                    Commands.literal("set").then(
                        Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(::suggestOptionValue)
                            .executes { ctx -> setOption(ctx, afterChange) }
                    )
                )
        )
        .then(
            Commands.literal("reset")
                .executes { ctx -> resetOptionGroup(ctx, afterChange) }
                .then(
                    pathArgument().executes { ctx -> resetOptionPath(ctx, afterChange) }
                )
        )

    return Commands.literal("options").then(groupArgument)
}

private fun getOption(ctx: CommandContext<CommandSourceStack>): Int {
    val groupName = StringArgumentType.getString(ctx, "group")
    val group = requireOptionGroup(groupName)
    val options = requireSpiderOptions()
    val path = StringArgumentType.getString(ctx, "path")

    val value = Serializer.path.get(group.live(options), path)
        ?: throw commandError("\"$path\" does not exist in $groupName")

    ctx.source.sender.sendPlainMessage("$path = ${Serializer.serialize(value)}")
    return SUCCESS
}

private fun setOption(ctx: CommandContext<CommandSourceStack>, afterChange: () -> Unit): Int {
    val groupName = StringArgumentType.getString(ctx, "group")
    val group = requireOptionGroup(groupName)
    val options = requireSpiderOptions()
    val path = StringArgumentType.getString(ctx, "path")
    val raw = StringArgumentType.getString(ctx, "value")

    val constant = findValueConstant(raw)
    val value = constant?.produce() ?: try {
        // treat unparseable values as raw strings (enums, block ids)
        Serializer.gson.fromJson(raw, Any::class.java)
    } catch (_: JsonSyntaxException) {
        raw
    }

    if (!group.edit(options) { writeOption(it, path, value) }) {
        throw commandError("Could not set $path")
    }

    afterChange()

    val applied = constant?.name ?: Serializer.serialize(Serializer.path.get(group.live(options), path))
    ctx.source.sender.sendPlainMessage("$path = $applied")
    return SUCCESS
}

private fun resetOptionGroup(ctx: CommandContext<CommandSourceStack>, afterChange: () -> Unit): Int {
    val groupName = StringArgumentType.getString(ctx, "group")
    val group = requireOptionGroup(groupName)
    val options = requireSpiderOptions()
    val defaults = Serializer.toMap(group.default(options)) as Map<*, *>

    if (!group.edit(options) { Serializer.path.setAll(it, defaults) }) {
        throw commandError("Could not reset $groupName")
    }

    afterChange()
    ctx.source.sender.sendPlainMessage("Reset all of $groupName")
    return SUCCESS
}

private fun resetOptionPath(ctx: CommandContext<CommandSourceStack>, afterChange: () -> Unit): Int {
    val groupName = StringArgumentType.getString(ctx, "group")
    val group = requireOptionGroup(groupName)
    val options = requireSpiderOptions()
    val path = StringArgumentType.getString(ctx, "path")

    val value = Serializer.path.get(group.default(options), path)
        ?: throw commandError("\"$path\" does not exist in $groupName")

    if (!group.edit(options) { writeOption(it, path, value) }) {
        throw commandError("Could not reset $path")
    }

    afterChange()
    ctx.source.sender.sendPlainMessage("Reset $path to ${Serializer.serialize(value)}")
    return SUCCESS
}

private val PRESETS: Map<String, (segmentCount: Int, segmentLength: Double) -> SpiderOptions> = mapOf(
    "biped" to ::biped,
    "quadruped" to ::quadruped,
    "hexapod" to ::hexapod,
    "octopod" to ::octopod,
    "quadbot" to ::quadBot,
    "hexbot" to ::hexBot,
    "octobot" to ::octoBot,
)

private fun presetCommand(): LiteralArgumentBuilder<CommandSourceStack> {
    val name = Commands.argument("name", StringArgumentType.word())
        .suggests { _, builder -> suggest(builder, PRESETS.keys) }
        .executes { ctx -> applyPreset(ctx, null, null) }
        .then(
            Commands.argument("segments", IntegerArgumentType.integer(1))
                .executes { ctx -> applyPreset(ctx, IntegerArgumentType.getInteger(ctx, "segments"), null) }
                .then(
                    Commands.argument("segmentLength", DoubleArgumentType.doubleArg(0.0))
                        .executes { ctx ->
                            applyPreset(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "segments"),
                                DoubleArgumentType.getDouble(ctx, "segmentLength"),
                            )
                        }
                )
        )

    return Commands.literal("preset").then(name)
}

private fun applyPreset(ctx: CommandContext<CommandSourceStack>, segmentCount: Int?, segmentLength: Double?): Int {
    val name = StringArgumentType.getString(ctx, "name")
    val createPreset = PRESETS[name] ?: throw commandError("Unknown preset \"$name\"")

    val spider = requireSpider(ctx.source)
    val options = createPreset(segmentCount ?: if (name.contains("bot")) 4 else 3, segmentLength ?: 1.0)

    spider.query<SpiderOptions>()?.copyFrom(options) ?: throw commandError("Spider has no options component")
    ctx.source.sender.sendPlainMessage("Applied preset $name")
    return SUCCESS
}

private fun scaleCommand(afterChange: () -> Unit): LiteralArgumentBuilder<CommandSourceStack> =
    Commands.literal("scale").then(
        Commands.argument("scale", DoubleArgumentType.doubleArg(0.0))
            .executes { ctx ->
                val scale = DoubleArgumentType.getDouble(ctx, "scale")

                val (spider, options) = AppState.ecs.query<SpiderBody, SpiderOptions>().firstOrNull()
                    ?: throw commandError("No spider found")

                val oldScale = options.bodyPlan.scale
                options.walkGait.scale(scale / oldScale)
                options.gallopGait.scale(scale / oldScale)
                options.bodyPlan.scale(scale / oldScale)
                spider.updateBodyPlan()

                afterChange()
                ctx.source.sender.sendPlainMessage("Set scale to $scale")
                SUCCESS
            }
    )

private fun splayCommand(): LiteralArgumentBuilder<CommandSourceStack> =
    Commands.literal("splay")
        .executes { ctx -> splaySpider(ctx, 0L) }
        .then(
            Commands.argument("delay", IntegerArgumentType.integer(0))
                .executes { ctx -> splaySpider(ctx, IntegerArgumentType.getInteger(ctx, "delay").toLong()) }
        )

private fun splaySpider(ctx: CommandContext<CommandSourceStack>, delay: Long): Int {
    val spider = requireSpider(ctx.source)
    runLater(delay) { splay(spider) }
    return SUCCESS
}

private fun itemsCommand(): LiteralArgumentBuilder<CommandSourceStack> =
    Commands.literal("items").executes { ctx ->
        val player = ctx.source.sender as? Player
            ?: throw commandError("This command can only be used by players")
        openCustomItemInventory(player)
        SUCCESS
    }

private fun writeOption(target: Any, path: String, value: Any?): Boolean {
    if (path.isNotEmpty()) return Serializer.path.set(target, path, value)

    val root = value ?: return false
    val fields = Serializer.toMap(root) as? Map<*, *> ?: return false
    return Serializer.path.setAll(target, fields)
}

private fun commandError(message: String) = SimpleCommandExceptionType({ message }).create()

private fun suggest(builder: SuggestionsBuilder, options: Iterable<String>): CompletableFuture<Suggestions> {
    val remaining = builder.remaining.lowercase()
    for (option in options) if (option.lowercase().startsWith(remaining)) builder.suggest(option)
    return builder.buildFuture()
}

private fun spiderOptionsOrNull() = AppState.ecs.query<SpiderOptions>().firstOrNull()

private fun requireSpiderOptions() = spiderOptionsOrNull() ?: throw commandError("No spider found")

private fun CommandSourceStack.senderLocation() =
    if (sender !is ConsoleCommandSender) location else throw commandError("This command can only be used by senders with a location")

private fun requireSpider(source: CommandSourceStack): ECSEntity {
    val location = source.senderLocation()
    return AppState.findNearestSpider(location) ?: throw commandError("No spider found")
}

private fun suggestionSource(group: OptionGroup): Any {
    val options = spiderOptionsOrNull() ?: return group.default(fallbackOptions)
    return group.live(options)
}

private class OptionGroup(
    val live: (SpiderOptions) -> Any,
    val default: (SpiderOptions) -> Any,
    val swap: ((options: SpiderOptions, draft: Any) -> Boolean)? = null,
) {
    fun edit(options: SpiderOptions, edit: (target: Any) -> Boolean): Boolean {
        // edit in place
        if (swap == null) return edit(live(options))

        // edit draft then swap
        val draft = Serializer.copyOf(live(options))
        return edit(draft) && swap.invoke(options, draft)
    }
}

private val OPTION_GROUPS = mapOf(
    "spider_options" to OptionGroup(
        live = { it },
        default = { defaultPreset() },
        swap = { options, draft -> options.copyFrom(draft as SpiderOptions); true },
    ),
    "global" to OptionGroup(live = { AppState.miscOptions }, default = { MiscellaneousOptions() }),
)

private fun requireOptionGroup(groupName: String): OptionGroup =
    OPTION_GROUPS[groupName]
        ?: throw commandError("Unknown option group \"$groupName\", expected one of ${OPTION_GROUPS.keys.joinToString()}")

private fun suggestOptionPaths(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
    val group = OPTION_GROUPS[StringArgumentType.getString(ctx, "group")] ?: return builder.buildFuture()

    // Quoted string if needed. See NBT path comment.
    val typed = builder.remaining.removePrefix("\"")
    for (path in Serializer.path.completions(suggestionSource(group), typed)) {
        val unquotable = path.all(StringReader::isAllowedInUnquotedString)
        builder.suggest(if (unquotable) path else "\"$path\"")
    }
    return builder.buildFuture()
}

private fun suggestOptionValue(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
    val group = OPTION_GROUPS[StringArgumentType.getString(ctx, "group")] ?: return builder.buildFuture()

    val current = Serializer.path.get(suggestionSource(group), StringArgumentType.getString(ctx, "path"))
        ?: return builder.buildFuture()

    for ((text, tooltip) in valueSuggestions(current)) {
        if (tooltip == null) builder.suggest(text) else builder.suggest(text, Message { tooltip })
    }
    return builder.buildFuture()
}

private val fallbackOptions: SpiderOptions by lazy { SpiderOptions() }

/**
 * Returns Pair<suggestion, tooltip>[]
 */
private fun valueSuggestions(current: Any): List<Pair<String, String?>> {
    val constants = valueConstantsFor(current).map { it.name to describeType(it) }

    // don't suggest json if constants are available
    if (constants.isNotEmpty()) return constants

    return when (current) {
        is Boolean -> listOf("true" to "boolean", "false" to "boolean")

        is Enum<*> -> current.javaClass.enumConstants
            .map { (it as Enum<*>).name to "${current.javaClass.simpleName} value" }

        is BlockData -> blockIds.map { it to "BlockData" }

        is Number, is Char -> listOf(current.toString() to current.javaClass.simpleName)

        is String -> if (needsQuoting(current)) {
            listOf(Serializer.serialize(current) to "String")
        } else {
            listOf(current to "String")
        }

        else -> {
            val json = Serializer.serialize(current)
            val type = describeType(current)
            if (json.length <= MAX_VALUE_SUGGESTION_LENGTH) listOf(json to type)
            else listOf(type to type)
        }
    }
}

private const val MAX_VALUE_SUGGESTION_LENGTH = 200

private fun needsQuoting(text: String): Boolean = try {
    Serializer.gson.fromJson(text, Any::class.java) !is String
} catch (_: Exception) {
    false
}

private fun describeType(value: Any): String = when (value) {
    is Collection<*> -> value.firstOrNull()?.let { "List<${it.javaClass.simpleName}>" } ?: "List<unknown>"
    is Map<*, *> -> "map"
    else -> value.javaClass.simpleName
}

private val blockIds: List<String> by lazy {
    Material.entries.filter { it.isBlock }.map { it.key.toString() }
}
