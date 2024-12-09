package com.heledron.spideranimation.spider.configuration

class SpiderOptions {
    var walkGait = Gait.defaultWalk()
    var gallopGait = Gait.defaultGallop()
    var bodyPlan = hexBotBodyPlan(3, 1.0)
    var debug = SpiderDebugOptions()

    fun scale(scale: Double) {
        walkGait.scale(scale)
        gallopGait.scale(scale)
        bodyPlan.scale(scale)
    }
}