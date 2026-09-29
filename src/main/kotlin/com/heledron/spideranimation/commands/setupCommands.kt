package com.heledron.spideranimation.commands

import com.google.gson.JsonPrimitive
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
            ctx.source.sender.sendPlainMessage("Usage: /spider <options|scale|splay|items>")
            SUCCESS
        }
        .then(optionsCommand(afterChange))
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
        Commands.argument("path", StringArgumentType.string())
            .suggests { _, builder -> suggestOptionPath(builder) }

    return Commands.literal("options").then(
        pathArgument()
            .executes(::getOption)
            .then(
                Commands.literal("set").then(
                    Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(::suggestOptionValue)
                        .executes { ctx -> setOption(ctx, afterChange) }
                )
            )
            .then(
                Commands.literal("reset").executes { ctx -> resetOption(ctx, afterChange) }
            )
    )
}

private fun parsePath(option: String): Pair<String, String> {
    val separator = option.indexOf('.')
    return if (separator < 0) option to ""
    else option.substring(0, separator) to option.substring(separator + 1)
}

private fun getOption(ctx: CommandContext<CommandSourceStack>): Int {
    val rawPath = StringArgumentType.getString(ctx, "path")
    val (groupName, subPath) = parsePath(rawPath)
    val group = requireOptionGroup(groupName)
    val live = group.requireLive()

    val value = Serializer.path.get(live, subPath)
        ?: throw commandError("\"$subPath\" does not exist in $groupName")

    ctx.source.sender.sendPlainMessage("$rawPath is ${Serializer.serialize(value)}")
    return SUCCESS
}

private fun setOption(ctx: CommandContext<CommandSourceStack>, afterChange: () -> Unit): Int {
    val rawPath = StringArgumentType.getString(ctx, "path")
    val (groupName, subPath) = parsePath(rawPath)
    val group = requireOptionGroup(groupName)
    val live = group.requireLive()
    val raw = StringArgumentType.getString(ctx, "value")

    val value = resolveSymbol(raw) ?: try {
        Serializer.gson.fromJson(raw, Any::class.java)
    } catch (_: JsonSyntaxException) {
        // treat unparseable values as raw strings (enums, block ids)
        raw
    }

    if (!group.edit(live) { writeOption(it, subPath, value) }) {
        throw commandError("Could not set $rawPath")
    }

    afterChange()

    ctx.source.sender.sendPlainMessage("Set $rawPath to $raw")
    return SUCCESS
}

private fun resetOption(ctx: CommandContext<CommandSourceStack>, afterChange: () -> Unit): Int {
    val rawPath = StringArgumentType.getString(ctx, "path")
    val (groupName, subPath) = parsePath(rawPath)
    val group = requireOptionGroup(groupName)
    val live = group.requireLive()

    val value = Serializer.path.get(group.default(), subPath)
        ?: throw commandError("\"$rawPath\" does not exist")

    if (!group.edit(live) { writeOption(it, subPath, value) }) {
        throw commandError("Could not reset $rawPath")
    }

    afterChange()

    if (subPath.isEmpty()) ctx.source.sender.sendPlainMessage("Reset all of $groupName")
    else ctx.source.sender.sendPlainMessage("Reset $rawPath to ${Serializer.serialize(value)}")
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

private fun CommandSourceStack.senderLocation() =
    if (sender !is ConsoleCommandSender) location else throw commandError("This command can only be used by senders with a location")

private fun requireSpider(source: CommandSourceStack): ECSEntity {
    val location = source.senderLocation()
    return AppState.findNearestSpider(location) ?: throw commandError("No spider found")
}

private fun suggestionSource(group: OptionGroup): Any = group.live() ?: group.default()

private class OptionGroup(
    val live: () -> Any?,
    val default: () -> Any,
    val swap: ((live: Any, draft: Any) -> Boolean)? = null,
) {
    fun edit(live: Any, edit: (target: Any) -> Boolean): Boolean {
        // edit in place
        if (swap == null) return edit(live)

        // edit draft then swap
        val draft = Serializer.copyOf(live)
        return edit(draft) && swap.invoke(live, draft)
    }
}

private fun OptionGroup.requireLive(): Any = live() ?: throw commandError("No spider found")

private val OPTION_GROUPS = mapOf(
    "spider_options" to OptionGroup(
        live = { spiderOptionsOrNull() },
        default = { defaultPreset() },
        swap = { live, draft -> (live as SpiderOptions).copyFrom(draft as SpiderOptions); true },
    ),
    "global" to OptionGroup(
        live = { AppState.miscOptions },
        default = { MiscellaneousOptions() },
    ),
)

private fun requireOptionGroup(groupName: String): OptionGroup =
    OPTION_GROUPS[groupName]
        ?: throw commandError("Unknown option group \"$groupName\", expected one of ${OPTION_GROUPS.keys.joinToString()}")

private fun suggestOptionPath(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
    // Quoted string if needed. See NBT path comment.
    val typed = builder.remaining.removePrefix("\"")
    val (groupName, path) = parsePath(typed)

    // Suggest groups
    if (path.isEmpty()) suggest(builder, OPTION_GROUPS.keys)

    // An exact group name falls through
    val group = OPTION_GROUPS[groupName] ?: return builder.buildFuture()

    for (candidate in Serializer.path.completions(suggestionSource(group), path)) {
        val argument = "$groupName.$candidate"
        val unquotable = argument.all(StringReader::isAllowedInUnquotedString)
        builder.suggest(if (unquotable) argument else "\"$argument\"")
    }
    return builder.buildFuture()
}

private fun suggestOptionValue(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
    val (groupName, path) = parsePath(StringArgumentType.getString(ctx, "path"))
    val group = OPTION_GROUPS[groupName] ?: return builder.buildFuture()

    val current = Serializer.path.get(suggestionSource(group), path)
        ?: return builder.buildFuture()

    for ((text, tooltip) in valueSuggestions(current)) {
        if (tooltip == null) builder.suggest(text) else builder.suggest(text, Message { tooltip })
    }
    return builder.buildFuture()
}

/**
 * Returns Pair<suggestion, tooltip>[]
 */
private fun valueSuggestions(current: Any): List<Pair<String, String?>> {
    val symbols = suggestSymbolsFor(current)

    // don't suggest json if symbols are available
    if (symbols.isNotEmpty()) return symbols

    return when (current) {
        is Boolean -> listOf("true" to "boolean", "false" to "boolean")

        is Enum<*> -> current.javaClass.enumConstants
            .map { (it as Enum<*>).name to "${current.javaClass.simpleName} value" }

        is BlockData -> blockIds.map { it to "BlockData" }

        is Number, is Char -> listOf(current.toString() to current.javaClass.simpleName)

        else -> {
            val type = describeType(current)

            val text = (Serializer.gson.toJsonTree(current) as? JsonPrimitive)?.takeIf { it.isString }?.asString
            if (text == null) {
                val json = Serializer.serialize(current)
                if (json.length <= MAX_VALUE_SUGGESTION_LENGTH) listOf(json to type)
                else listOf(type to type)
            } else {
                val suggestion = if (needsQuoting(text)) Serializer.serialize(text) else text
                listOf(suggestion to type)
            }
        }
    }
}

private const val MAX_VALUE_SUGGESTION_LENGTH = 200

private fun needsQuoting(text: String): Boolean = try {
    Serializer.gson.fromJson(text, Any::class.java) !is String
} catch (_: Exception) {
    false
}

internal fun describeType(value: Any): String = when (value) {
    is Collection<*> -> value.firstOrNull()?.let { "List<${it.javaClass.simpleName}>" } ?: "List<unknown>"
    is Map<*, *> -> "map"
    else -> value.javaClass.simpleName
}

private val blockIds: List<String> by lazy {
    Material.entries.filter { it.isBlock }.map { it.key.toString() }
}
