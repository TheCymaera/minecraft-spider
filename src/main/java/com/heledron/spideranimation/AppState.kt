package com.heledron.spideranimation

import com.heledron.spideranimation.spider.*
import org.bukkit.Location

object AppState {
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
    var spiderOptions = SpiderOptions()

    var bodyPlan = quadrupedBodyPlan().create()


    fun createSpider(location: Location): Spider {
        location.y += walkGait.bodyHeight
        return Spider(location, bodyPlan)
    }

    fun update() {
        spider?.gallopGait = gallopGait
        spider?.walkGait = walkGait
        spider?.options = spiderOptions
    }
}