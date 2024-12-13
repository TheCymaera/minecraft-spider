package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.presets.hexBotBodyPlan

class SpiderOptions {
    var stationaryGait = Gait.stationary()
    var movingButNotWalkingGait = Gait.movingButNotWalking()
    var walkGait = MoveGait.defaultWalk()
    var gallopGait = MoveGait.defaultGallop()

    var bodyPlan = hexBotBodyPlan(3, 1.0)
    var debug = SpiderDebugOptions()

    fun scale(scale: Double) {
        stationaryGait.scale(scale)
        movingButNotWalkingGait.scale(scale)
        walkGait.scale(scale)
        gallopGait.scale(scale)
        bodyPlan.scale(scale)
    }
}