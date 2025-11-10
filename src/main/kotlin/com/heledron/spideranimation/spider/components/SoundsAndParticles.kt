package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.Leg
import com.heledron.spideranimation.spider.components.body.LegStepEvent
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.body.SpiderBodyHitGroundEvent
import com.heledron.spideranimation.spider.configuration.SoundOptions
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.utilities.ECS
import com.heledron.spideranimation.utilities.playSound
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.data.Waterlogged
import org.bukkit.util.Vector
import java.util.*
import kotlin.collections.set
import kotlin.random.Random

class SoundsAndParticles(var options: SoundOptions) {
    var timeSinceLastSound = 0
    var wetness = WeakHashMap<Leg, Int>()
    val maxWetness = 20 * 3

    fun underwaterStepSound() = SoundPlayer(
        sound = options.step.sound,
        volume = options.step.volume * .5f,
        pitch = options.step.pitch * .75f,
        volumeVary = options.step.volumeVary,
        pitchVary = options.step.pitchVary
    )
}

fun setupSoundAndParticles(app: ECS) {
    app.onEvent<SpiderBodyHitGroundEvent> { event ->
        event.spider.world.playSound(event.spider.position, Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.0f, .8f)
    }

    app.onEvent<TridentHitEvent> { event ->
        event.spider.world.playSound(event.spider.position, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .5f, 1.0f)
    }

    app.onEvent<CloakToggleEvent> { event ->
        event.spider.world.playSound(event.spider.position, Sound.BLOCK_LODESTONE_PLACE, 1.0f, 0.0f)
    }

    app.onEvent<CloakDamageEvent> { event ->
        event.spider.world.playSound(event.spider.position, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, .5f, 1.5f)
        event.spider.world.playSound(event.spider.position, Sound.BLOCK_LODESTONE_PLACE, 0.1f, 0.0f)
        event.spider.world.playSound(event.spider.position, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, .02f, 1.5f)
    }

    app.onEvent<LegStepEvent> { event ->
        val isUnderWater = event.spider.world.getBlockAt(event.leg.endEffector.toLocation(event.spider.world)).isLiquid

        val sounds = event.entity.query<SoundsAndParticles>() ?: return@onEvent

        val sound = if (isUnderWater) sounds.underwaterStepSound() else sounds.options.step

        sound.play(event.spider.world, event.leg.endEffector)
    }

    app.onTick {
        for ((spider, sounds) in app.query<SpiderBody, SoundsAndParticles>()) {
            sounds.timeSinceLastSound++

            for (leg in spider.legs) {
                spawnLegParticles(sounds, spider.world, leg)
            }
        }
    }
}

private fun isInWater(world: World, position: Vector): Boolean {
    val block = world.getBlockAt(position.toLocation(world))
    return block.isLiquid || (block is Waterlogged && block.isWaterlogged)
}


private fun spawnLegParticles(sounds: SoundsAndParticles, world: World, leg: Leg) {
    val justBegunMoving = leg.isMoving && leg.timeSinceBeginMove < 1
    val wasUnderWater = isInWater(world, leg.previousEndEffector)
    val isUnderWater = isInWater(world, leg.endEffector)
    val justEnteredWater = isUnderWater && !wasUnderWater
    val justExitedWater = !isUnderWater && wasUnderWater

    if (isUnderWater) sounds.wetness[leg] = sounds.maxWetness
    else sounds.wetness[leg] = (sounds.wetness[leg] ?: 1) - 1

    // sound
    if (sounds.timeSinceLastSound > 20) {
        if (justEnteredWater) {
            val volume = .3f
            val pitch = 1.0f + Random.nextFloat() * 0.1f
            world.playSound(leg.endEffector, Sound.ENTITY_PLAYER_SPLASH, volume, pitch)
            sounds.timeSinceLastSound = 0
        }
        else if (justExitedWater) {
            val volume = .3f
            val pitch = 1f + Random.nextFloat() * 0.1f
            world.playSound(leg.endEffector, Sound.AMBIENT_UNDERWATER_EXIT, volume, pitch)
            sounds.timeSinceLastSound = 0
        }
        else if (justBegunMoving && isUnderWater) {
            val volume = .3f
            val pitch = .7f + Random.nextFloat() * 0.1f
            world.playSound(leg.endEffector, Sound.ENTITY_PLAYER_SWIM, volume, pitch)
            sounds.timeSinceLastSound = 0
        }
    }


    // particles
    val wetness = sounds.wetness[leg] ?: 0
    for (segment in leg.chain.segments) {
        val segmentIsUnderWater = isInWater(world, segment.position)

        val location = segment.position.toLocation(world).add(.0, -.1, .0)

        if (segmentIsUnderWater) {
            if (justEnteredWater || justExitedWater) {
                val offset = .3
                world.spawnParticle(Particle.SPLASH, location, 40, offset, .1, offset)
            } else if (leg.isMoving) {
                val offset = .1
                world.spawnParticle(Particle.BUBBLE, location, 1, offset, .0, offset, .0)
            }
        }

        if (wetness > 0) {
            if (Random.nextInt(0, sounds.maxWetness) > wetness) continue

            val offset = .0
            world.spawnParticle(Particle.FALLING_WATER, location, 1, offset, offset, offset, .1)
        }
    }
}