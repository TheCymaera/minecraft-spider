package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.body.setupSpiderBody
import com.heledron.spideranimation.spider.misc.*
import com.heledron.spideranimation.spider.rendering.setupRenderer
import com.heledron.spideranimation.utilities.*

fun setupSpider(app: ECS) {
    setupSpiderBody(app)
    setupBehaviours(app)
    setupCloak(app)
    setupMountable(app)
    setupPointDetector(app)
    setupSoundAndParticles(app)
    setupTridentHitDetector(app)
    setupRenderer(app)
}
