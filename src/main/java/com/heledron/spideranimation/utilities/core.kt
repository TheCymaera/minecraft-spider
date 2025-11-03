package com.heledron.spideranimation.utilities

import com.heledron.spideranimation.utilities.events.onTick
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.CommandMinecart
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import java.io.InputStream

lateinit var currentPlugin: JavaPlugin
var currentTick = 0

private val closeableList = mutableListOf<Closeable>()

fun onPluginShutdown(task: () -> Unit) = closeableList.add(task)

fun JavaPlugin.setupCoreUtils() {
    currentPlugin = this
    onTick { currentTick ++ }
}

fun JavaPlugin.shutdownCoreUtils() {
    closeableList.forEach { it.close() }
}

fun namespacedID(id: String): NamespacedKey {
    return NamespacedKey(currentPlugin, id)
}

fun requireResource(name: String): InputStream {
    return currentPlugin.getResource(name) ?: error("Resource $name not found")
}

fun requireCommand(name: String): PluginCommand {
    return currentPlugin.getCommand(name) ?: error("Command $name not found")
}

private var commandBlockMinecart: CommandMinecart? = null
fun runCommandSilently(command: String, location: Location = Bukkit.getWorlds().first().spawnLocation) {
    val server = Bukkit.getServer()

    val commandBlockMinecart = commandBlockMinecart ?: spawnEntity(location, CommandMinecart::class.java) {
        commandBlockMinecart = it
        it.remove()
    }

    server.dispatchCommand(commandBlockMinecart, command)
}

fun CommandSender.sendActionBarOrMessage(message: String) {
    if (this is Player) {
        this.sendActionBar(message)
    } else {
        this.sendMessage(message)
    }
}

fun Player.sendActionBar(message: String) {
    this.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message))
}

fun sendDebugMessage(message: String) {
    // send action bar
    Bukkit.getOnlinePlayers().firstOrNull()?.sendActionBar(message)
}

fun <T : Entity> spawnEntity(location: Location, clazz: Class<T>, initializer: (T) -> Unit): T {
    return location.world!!.spawn(location, clazz, initializer)
}

fun playSound(location: Location, sound: Sound, volume: Float, pitch: Float) {
    location.world!!.playSound(location, sound, volume, pitch)
}