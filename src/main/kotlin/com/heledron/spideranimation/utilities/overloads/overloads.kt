package com.heledron.spideranimation.utilities.overloads

import com.heledron.spideranimation.utilities.maths.pitchRadians
import com.heledron.spideranimation.utilities.maths.yawRadians
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector


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

fun sendDebugActionBar(message: String) {
    Bukkit.getOnlinePlayers().firstOrNull()?.sendActionBar(message)
}

fun sendDebugChatMessage(message: String) {
    Bukkit.getOnlinePlayers().firstOrNull()?.sendMessage(message)
}

fun <T : Entity> World.spawnEntity(position: Vector, clazz: Class<T>, initializer: (T) -> Unit): T {
    return this.spawn(position.toLocation(this), clazz, initializer)
}

fun World.playSound(position: Vector, sound: Sound, volume: Float, pitch: Float) {
    this.playSound(position.toLocation(this), sound, volume, pitch)
}

val Entity.position get() = this.location.toVector()
val LivingEntity.eyePosition get() = this.eyeLocation.toVector()
val Entity.direction get() = this.location.direction
val Entity.yaw get() = this.location.yaw
val Entity.pitch get() = this.location.pitch
fun Entity.yawRadians() = this.location.yawRadians()
fun Entity.pitchRadians() = this.location.pitchRadians()