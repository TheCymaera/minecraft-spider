package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.components.body.setupSpiderBody
import com.heledron.spideranimation.spider.components.*
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.rendering.setupRenderer
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity

fun setupSpider(app: ECS) {
    setupSpiderBody(app)
    setupBehaviours(app)

    app.onTick {
        for ((entity, _) in app.query<ECSEntity, SpiderBody>()) {
            entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
        }
    }

    setupCloak(app)
    setupMountable(app)
    setupPointDetector(app)
    setupSoundAndParticles(app)
    setupTridentHitDetector(app)
    setupRenderer(app)
}
