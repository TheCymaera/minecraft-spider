package com.heledron.spideranimation

import com.heledron.spideranimation.spider.*
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.configuration.hexBotBodyPlan
import org.bukkit.Location

object AppState {
    var showDebugVisuals = true
    var gallop = false

    var spider: Spider? = null
    set (value) {
        field?.close()
        field = value
    }

    var target: Location? = null

    var chainVisualizer: KinematicChainVisualizer? = null
    set (value) {
        field?.close()
        field = value
    }

    var options = SpiderOptions()
    var miscOptions = MiscellaneousOptions()

    fun createSpider(location: Location): Spider {
        location.y += options.walkGait.bodyHeight
        return Spider(location, options)
    }

    fun update() {
//        spider?.options = options
        spider?.showDebugVisuals = showDebugVisuals
        spider?.gallop = gallop
    }
}

class MiscellaneousOptions {
    var showLaser = true
}