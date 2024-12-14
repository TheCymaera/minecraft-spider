package com.heledron.spideranimation.spider.configuration

class SpiderOptions {
    var walkGait = Gait.defaultWalk()
    var gallopGait = Gait.defaultGallop()

    var cloak = CloakOptions()

    var bodyPlan = BodyPlan()
    var debug = SpiderDebugOptions()

    fun scale(scale: Double) {
        walkGait.scale(scale)
        gallopGait.scale(scale)
        bodyPlan.scale(scale)
    }
}

