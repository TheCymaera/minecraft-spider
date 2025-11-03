package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.spider.body.Leg
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.utilities.playSound
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.Waterlogged
import org.bukkit.util.Vector
import java.io.Closeable
import java.util.*
import kotlin.random.Random

class SoundsAndParticles(val spider: Spider) : SpiderComponent {
    var closeables = mutableListOf<Closeable>()

    val underwaterStepSound: SoundPlayer; get() = SoundPlayer(
        sound = spider.options.sound.step.sound,
        volume = spider.options.sound.step.volume * .5f,
        pitch = spider.options.sound.step.pitch * .75f,
        volumeVary = spider.options.sound.step.volumeVary,
        pitchVary = spider.options.sound.step.pitchVary
    )

    override fun close() {
        closeables.forEach { it.close() }
        closeables = mutableListOf()
    }

    init {
        closeables += spider.body.onHitGround.listen {
            playSound(spider.location(), Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.0f, .8f)
        }

        closeables += spider.tridentDetector.onHit.listen {
            playSound(spider.location(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .5f, 1.0f)
        }

        closeables += spider.cloak.onToggle.listen {
            playSound(spider.location(), Sound.BLOCK_LODESTONE_PLACE, 1.0f, 0.0f)
        }

        closeables += spider.cloak.onCloakDamage.listen {
            playSound(spider.location(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, .5f, 1.5f)
            playSound(spider.location(), Sound.BLOCK_LODESTONE_PLACE, 0.1f, 0.0f)
            playSound(spider.location(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, .02f, 1.5f)
        }

        for (leg in spider.body.legs) {
            closeables += leg.onStep.listen {
                val isUnderWater = spider.world.getBlockAt(leg.endEffector.toLocation(spider.world)).isLiquid
                val sound = if (isUnderWater) underwaterStepSound else spider.options.sound.step

                sound.play(spider.world, leg.endEffector)
            }
        }
    }

    private fun isInWater(position: Vector): Boolean {
        val block = spider.world.getBlockAt(position.toLocation(spider.world))
        return block.isLiquid || (block is Waterlogged && block.isWaterlogged)
    }

    var timeSinceLastSound = 0
    var wet = WeakHashMap<Leg, Int>()
    val maxWetness = 20 * 3
    override fun update() {
        timeSinceLastSound++

        for (leg in spider.body.legs) {
            val justBegunMoving = leg.isMoving && leg.timeSinceBeginMove < 1
            val wasUnderWater = isInWater(leg.previousEndEffector)
            val isUnderWater = isInWater(leg.endEffector)
            val justEnteredWater = isUnderWater && !wasUnderWater
            val justExitedWater = !isUnderWater && wasUnderWater

            if (isUnderWater) wet[leg] = maxWetness
            else wet[leg] = (wet[leg] ?: 1) - 1

            // sound
            if (timeSinceLastSound > 20) {
                if (justEnteredWater) {
                    val volume = .3f
                    val pitch = 1.0f + Random.nextFloat() * 0.1f
                    playSound(leg.endEffector.toLocation(spider.world), Sound.ENTITY_PLAYER_SPLASH, volume, pitch)
                    timeSinceLastSound = 0
                }
                else if (justExitedWater) {
                    val volume = .3f
                    val pitch = 1f + Random.nextFloat() * 0.1f
                    playSound(leg.endEffector.toLocation(spider.world), Sound.AMBIENT_UNDERWATER_EXIT, volume, pitch)
                    timeSinceLastSound = 0
                }
                else if (justBegunMoving && isUnderWater) {
                    val volume = .3f
                    val pitch = .7f + Random.nextFloat() * 0.1f
                    playSound(leg.endEffector.toLocation(spider.world), Sound.ENTITY_PLAYER_SWIM, volume, pitch)
                    timeSinceLastSound = 0
                }
            }


            // particles
            val wetness = wet[leg] ?: 0
            for (segment in leg.chain.segments) {
                val segmentIsUnderWater = isInWater(segment.position)

                val location = segment.position.toLocation(spider.world).add(.0, -.1, .0)

                if (segmentIsUnderWater) {
                    if (justEnteredWater || justExitedWater) {
                        val offset = .3
                        spider.world.spawnParticle(Particle.SPLASH, location, 40, offset, .1, offset)
                    } else if (leg.isMoving) {
                        val offset = .1
                        spider.world.spawnParticle(Particle.BUBBLE, location, 1, offset, .0, offset, .0)
                    }
                }

                if (wetness > 0) {
                    if (Random.nextInt(0, maxWetness) > wetness) continue

                    val offset = .0
                    spider.world.spawnParticle(Particle.FALLING_WATER, location, 1, offset, offset, offset, .1)
                }
            }
        }
    }
}
