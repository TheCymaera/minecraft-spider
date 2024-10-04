package com.heledron.spideranimation.spider

import com.heledron.spideranimation.utilities.playSound
import org.bukkit.Sound
import java.io.Closeable

class SoundEffects(val spider: Spider) : SpiderComponent {
    var closeables = mutableListOf<Closeable>()

    override fun close() {
        closeables.forEach { it.close() }
        closeables = mutableListOf()
    }

    init {
        closeables += spider.body.onHitGround.listen {
            playSound(spider.location, Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.0f, .8f)
        }

        closeables += spider.body.onGetHitByTrident.listen {
            playSound(spider.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .5f, 1.0f)
        }

        closeables += spider.cloak.onToggle.listen {
            playSound(spider.location, Sound.BLOCK_LODESTONE_PLACE, 1.0f, 0.0f)
        }

        closeables += spider.cloak.onCloakDamage.listen {
            playSound(spider.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, .5f, 1.5f)
            playSound(spider.location, Sound.BLOCK_LODESTONE_PLACE, 0.1f, 0.0f)
            playSound(spider.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, .02f, 1.5f)
        }

        for (leg in spider.body.legs) {
            // Step sound effect
            closeables += leg.onStep.listen {
                val location = leg.endEffector.toLocation(spider.location.world!!)
                val volume = .3f
                val pitch = 1.0f + Math.random().toFloat() * 0.1f
                playSound(location, Sound.BLOCK_NETHERITE_BLOCK_STEP, volume, pitch)
            }

        }
    }
}