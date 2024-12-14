package com.heledron.spideranimation

import com.heledron.spideranimation.spider.*
import com.heledron.spideranimation.spider.presets.hexBot
import com.heledron.spideranimation.utilities.MultiEntityRenderer
import org.bukkit.Location

object AppState {
    val renderer = MultiEntityRenderer()

    var options = hexBot(4, 1.0)
    var miscOptions = MiscellaneousOptions()

    var showDebugVisuals = true
    var gallop = false

    var spider: Spider? = null
    set (value) {
        if (field != value) field?.close()
        field = value
    }

    var target: Location? = null

    var chainVisualizer: KinematicChainVisualizer? = null
    set (value) {
        if (field != value) field?.close()
        field = value
    }

    fun createSpider(location: Location): Spider {
        location.y += options.walkGait.stationary.bodyHeight
        val spider = Spider.fromLocation(location, options)
        this.spider = spider
        return spider
    }

    fun recreateSpider() {
        val location = this.spider?.location() ?: return
        createSpider(location)
    }
}

class MiscellaneousOptions {
    var showLaser = true
}