package com.heledron.spideranimation

import com.heledron.spideranimation.spider.*
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.MultiEntityRenderer
import org.bukkit.Location

object AppState {
    val renderer = MultiEntityRenderer()

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
        location.y += options.stationaryGait.bodyHeight
        return Spider.fromLocation(location, options)
    }
}

class MiscellaneousOptions {
    var showLaser = true
}