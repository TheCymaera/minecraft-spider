package com.heledron.spideranimation

fun setStage(gait: Gait, renderOptions: RenderDebugOptions, stage: Int): String? {
    val default = Gait()

    val stagesFunctions = listOf(
        {
            gait.legMovingTriggerDistance = 1000.0
            gait.legStationaryTriggerDistance = 1000.0
            gait.legDiscomfortDistance = 1000.0
            gait.rotateSpeed = 10000.0
            gait.bodyHeightCorrectionFactor = 0.0
            gait.legScanGround = false
            gait.applyGravity = false
            gait.legAlwaysCanMove = true
            gait.legScanAlternativeGround = true
            renderOptions.legRestPosition = false
            "No behaviour"
        },
        {
            gait.legMovingTriggerDistance = default.legMovingTriggerDistance
            gait.legStationaryTriggerDistance = default.legMovingTriggerDistance
            "Move to target"
        },
        {
            gait.legAlwaysCanMove = false
            "Only move when opposite legs are grounded"
        },
        {
            gait.legStationaryTriggerDistance = default.legStationaryTriggerDistance
            "Shrink trigger distance when spider is stationary"
        },
        {
            gait.rotateSpeed = default.rotateSpeed
            "Interpolate rotation"
        },
        {
            gait.legDiscomfortDistance = default.legDiscomfortDistance
            "Stop moving if leg is too far from rest position"
        },
        {
            gait.legScanGround = true
            renderOptions.legRestPosition = true
            "Apply height adjustment"
        },
        {
            gait.bodyHeightCorrectionFactor = default.bodyHeightCorrectionFactor
            "Apply body height adjustment"
        },
        {
            gait.applyGravity = true
            "Apply gravity"
        },
        {
            gait.legScanAlternativeGround = false
            gait.legApplyScanHeightBias = false
            "Scan multiple candidates"
        },
        {
            gait.legApplyScanHeightBias = true
            "Apply height bias"
        }
    )

    if (!(0 < stage && stage <= stagesFunctions.size)) {
        return null
    }

    var name: String? = null
    for (i in 0 until stage) {
        name = stagesFunctions[i]()
    }

    return name
}