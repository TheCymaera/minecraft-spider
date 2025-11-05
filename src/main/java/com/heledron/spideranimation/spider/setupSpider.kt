package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.components.body.setupSpiderBody
import com.heledron.spideranimation.spider.components.*
import com.heledron.spideranimation.spider.components.rendering.setupRenderer
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
