package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.components.body.GaitType
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.utilities.maths.horizontal
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.toRadians
import org.joml.Quaternionf


class LerpGait(
    var bodyHeight: Double,
    var triggerZoneRadius: Double,
) {
    fun scale(scale: Double): LerpGait {
        bodyHeight *= scale
        triggerZoneRadius *= scale
        return this
    }

    fun clone() = LerpGait(
        bodyHeight = bodyHeight,
        triggerZoneRadius = triggerZoneRadius,
    )

    fun lerp(target: LerpGait, factor: Double): LerpGait {
        this.bodyHeight = bodyHeight.lerp(target.bodyHeight, factor)
        this.triggerZoneRadius = triggerZoneRadius.lerp(target.triggerZoneRadius, factor)
        return this
    }
}


class Gait(
    walkSpeed: Double,
    val type: GaitType,
) {
    companion object {
        fun defaultWalk() = Gait(.15, GaitType.WALK)

        fun defaultGallop() = Gait(.4, GaitType.GALLOP).apply {
            moving.bodyHeight = 1.6
            legMoveSpeed = .5
            rotateAcceleration = .25f / 4
            uncomfortableSpeedMultiplier = .6
            samePairCooldown = 2
            crossPairCooldown = 4
            polygonLeeway = .5
        }
    }

    fun scale(scale: Double) {
        stationary.scale(scale)
        moving.scale(scale)
        maxBodyDistanceFromGround *= scale
        maxSpeed *= scale
        moveAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        comfortZoneRadius *= scale
        legScanHeightBias *= scale
        tridentRotationalKnockBack /= scale
    }

    var stationary = LerpGait(
        bodyHeight = 1.1,
        triggerZoneRadius = .25,
    )

    var moving = LerpGait(
        bodyHeight = 1.1,
        triggerZoneRadius = .8,
    )

    var maxBodyDistanceFromGround = .25

    var maxSpeed = walkSpeed
    var moveAcceleration = .15 / 4

    var rotateAcceleration = .15f / 4
    var rotationalDragCoefficient = .2f

    var legMoveSpeed = walkSpeed * 2.5

    var legLiftHeight = .35

    var comfortZoneRadius = 1.2

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

    var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    var bodyHeightCorrectionFactor = .25

    var legScanAlternativeGround = true
    var legScanHeightBias = .5

    var tridentKnockBack = .3
    var tridentRotationalKnockBack = tridentKnockBack / 4
    var legLookAheadFraction = .6
    var groundDragCoefficient = .2

    var samePairCooldown = 1
    var crossPairCooldown = 1

    var useLegacyNormalForce = false
    var polygonLeeway = .0
    
    // TODO: Consider removing this
    var stabilizationFactor = .0 //0.7

    var uncomfortableSpeedMultiplier = 0.0

    var disableAdvancedRotation = false
    var preferredPitchLeeway = 10f.toRadians()

    var straightenLegs = true
    var legStraightenRotation = (-80f).toRadians()

    var scanPivotMode = PivotMode.YAxis
    var legChainPivotMode = PivotMode.SpiderOrientation

    var preferLevelBreakpoint = 45f.toRadians()
    var preferLevelBias = .0f //.2f
    var preferredRotationLerpFraction = .3f

    var rotationLerp = .3f
}


enum class PivotMode(val get: (spider: SpiderBody) -> Quaternionf) {
    YAxis({ spider -> spider.orientation.horizontal() }),
    SpiderOrientation({ spider -> spider.orientation }),
    GroundOrientation({ spider -> spider.preferredOrientation })
}
