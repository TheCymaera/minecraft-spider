package com.heledron.spideranimation.spider.configuration

import org.bukkit.Sound
import org.bukkit.util.Vector
import kotlin.random.Random

class SpiderOptions {
    var walkGait = Gait.defaultWalk()
    var gallopGait = Gait.defaultGallop()

    var cloak = CloakOptions()

    var bodyPlan = BodyPlan()
    var debug = SpiderDebugOptions()

    var sound = SoundOptions()

    fun scale(scale: Double) {
        walkGait.scale(scale)
        gallopGait.scale(scale)
        bodyPlan.scale(scale)
    }
}



class SoundOptions {
    var step = SoundPlayer(
        sound = Sound.BLOCK_NETHERITE_BLOCK_STEP,
        volume = .3f,
        pitch = 1.0f
    )
}


class SoundPlayer(
    val sound: Sound,
    val volume: Float,
    val pitch: Float,
    val volumeVary: Float = 0.1f,
    val pitchVary: Float = 0.1f
) {
    fun play(world: org.bukkit.World, position: Vector) {
        val volume = volume + Random.nextFloat() * volumeVary
        val pitch = pitch + Random.nextFloat() * pitchVary
        world.playSound(position.toLocation(world), sound, volume, pitch)
    }
}