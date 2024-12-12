package com.heledron.spideranimation.spider.misc

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.EventEmitter
import com.heledron.spideranimation.utilities.runLater
import org.bukkit.entity.Trident

class TridentHitDetector(val spider: Spider): SpiderComponent {
    val onHit = EventEmitter()
    var stunned = false

    init {
        onHit.listen {
            stunned = true
            runLater(5) { stunned = false }
        }
    }

    override fun update() {
        val tridents = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), 1.5, 1.5, 1.5) {
            it is Trident && it.shooter != spider.mount.getRider()
        }
        for (trident in tridents) {
            if (trident != null && trident.velocity.length() > 2.0) {
                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                onHit.emit()

                spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))
            }
        }
    }
}