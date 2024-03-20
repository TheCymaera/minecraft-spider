package com.heledron.spideranimation

import org.bukkit.plugin.Plugin

fun saveMapToConfig(plugin: Plugin, name: String, map: HashMap<String, Any>) {
    val config = plugin.config

    for ((key, value) in map) {
        config.set("$name.$key", value)
    }

    plugin.saveConfig()
}

fun loadMapFromConfig(plugin: Plugin, name: String): HashMap<String, Any> {
    val config = plugin.config
    val map = HashMap<String, Any>()

    for (key in config.getConfigurationSection(name)?.getKeys(false) ?: emptySet()) {
        map[key] = config.get("$name.$key") as Any
    }

    return map
}

fun gaitToMap(gait: Gait): HashMap<String, Any> {
    val map = HashMap<String, Any>()

    map["walkSpeed"] = gait.walkSpeed
    map["walkAcceleration"] = gait.walkAcceleration
    map["rotateSpeed"] = gait.rotateSpeed

    map["legSpeed"] = gait.legSpeed
    map["legLiftHeight"] = gait.legLiftHeight
    map["legDropDistance"] = gait.legDropDistance

    map["legStationaryTriggerDistance"] = gait.legStationaryTriggerDistance
    map["legMovingTriggerDistance"] = gait.legMovingTriggerDistance
    map["legDiscomfortDistance"] = gait.legDiscomfortDistance

    map["gravityAcceleration"] = gait.gravityAcceleration
    map["airDragCoefficient"] = gait.airDragCoefficient
    map["bounceFactor"] = gait.bounceFactor

    map["bodyHeightCorrectionAcceleration"] = gait.bodyHeightCorrectionAcceleration
    map["bodyHeightCorrectionFactor"] = gait.bodyHeightCorrectionFactor
    map["applyGravity"] = gait.applyGravity
    map["bodyHeight"] = gait.bodyHeight

    map["legScanGround"] = gait.legScanGround
    map["legAlwaysCanMove"] = gait.legAlwaysCanMove
    map["legScanAlternativeGround"] = gait.legScanAlternativeGround
    map["legApplyScanHeightBias"] = gait.legApplyScanHeightBias

    map["legStraightenHeight"] = gait.legStraightenHeight
    map["legNoStraighten"] = gait.legNoStraighten
    map["legSegmentLength"] = gait.legSegmentLength
    map["legSegmentCount"] = gait.legSegmentCount

    return map
}

fun gaitFromMap(gait: Gait, map: Map<String, Any>) {
    (map["walkSpeed"] as? Double)?.apply { gait.walkSpeed = this }
    (map["walkAcceleration"] as? Double)?.apply { gait.walkAcceleration = this }
    (map["rotateSpeed"] as? Double)?.apply { gait.rotateSpeed = this }

    (map["legSpeed"] as? Double)?.apply { gait.legSpeed = this }
    (map["legLiftHeight"] as? Double)?.apply { gait.legLiftHeight = this }
    (map["legDropDistance"] as? Double)?.apply { gait.legDropDistance = this }

    (map["legStationaryTriggerDistance"] as? Double)?.apply { gait.legStationaryTriggerDistance = this }
    (map["legMovingTriggerDistance"] as? Double)?.apply { gait.legMovingTriggerDistance = this }
    (map["legDiscomfortDistance"] as? Double)?.apply { gait.legDiscomfortDistance = this }

    (map["gravityAcceleration"] as? Double)?.apply { gait.gravityAcceleration = this }
    (map["airDragCoefficient"] as? Double)?.apply { gait.airDragCoefficient = this }
    (map["bounceFactor"] as? Double)?.apply { gait.bounceFactor = this }

    (map["bodyHeightCorrectionAcceleration"] as? Double)?.apply { gait.bodyHeightCorrectionAcceleration = this }
    (map["bodyHeightCorrectionFactor"] as? Double)?.apply { gait.bodyHeightCorrectionFactor = this }
    (map["applyGravity"] as? Boolean)?.apply { gait.applyGravity = this }
    (map["bodyHeight"] as? Double)?.apply { gait.bodyHeight = this }

    (map["legScanGround"] as? Boolean)?.apply { gait.legScanGround = this }
    (map["legAlwaysCanMove"] as? Boolean)?.apply { gait.legAlwaysCanMove = this }
    (map["legScanAlternativeGround"] as? Boolean)?.apply { gait.legScanAlternativeGround = this }
    (map["legApplyScanHeightBias"] as? Boolean)?.apply { gait.legApplyScanHeightBias = this }

    (map["legStraightenHeight"] as? Double)?.apply { gait.legStraightenHeight = this }
    (map["legNoStraighten"] as? Boolean)?.apply { gait.legNoStraighten = this }
    (map["legSegmentLength"] as? Double)?.apply { gait.legSegmentLength = this }
    (map["legSegmentCount"] as? Int)?.apply { gait.legSegmentCount = this }

}