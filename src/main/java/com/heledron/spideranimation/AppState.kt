package com.heledron.spideranimation

import com.heledron.spideranimation.spider.*
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

    var walkGait = Gait.defaultWalk()
    var gallopGait = Gait.defaultGallop()
    var debugOptions = SpiderDebugOptions()
    var miscOptions = MiscellaneousOptions()

    var bodyPlan = quadrupedBodyPlan(segmentCount = 3, segmentLength = 1.0)

    fun createSpider(location: Location): Spider {
        location.y += walkGait.bodyHeight
        return Spider(location, bodyPlan)
    }

    fun update() {
        spider?.gallopGait = gallopGait
        spider?.walkGait = walkGait
        spider?.debugOptions = debugOptions
        spider?.showDebugVisuals = showDebugVisuals
        spider?.gallop = gallop
    }
}

class MiscellaneousOptions {
    var showLaser = true
}