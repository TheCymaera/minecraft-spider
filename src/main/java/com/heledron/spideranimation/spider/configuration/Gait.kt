package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.body.GaitType
import com.heledron.spideranimation.utilities.SplitDistance
import com.heledron.spideranimation.utilities.lerp
import com.heledron.spideranimation.utilities.toRadians
import org.joml.Quaternionf


class LerpGait(
    var bodyHeight: Double,
    var triggerZone: SplitDistance
) {
    fun scale(scale: Double): LerpGait {
        bodyHeight *= scale
        triggerZone = triggerZone.scale(scale)
        return this
    }

    fun clone() = LerpGait(
        bodyHeight = bodyHeight,
        triggerZone = triggerZone
    )

    fun lerp(target: LerpGait, factor: Double): LerpGait {
        this.bodyHeight = bodyHeight.lerp(target.bodyHeight, factor)
        this.triggerZone = triggerZone.lerp(target.triggerZone, factor)
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
            legMoveSpeed = .55
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
        maxSpeed *= scale
        moveAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        legDropDistance *= scale
        comfortZone = comfortZone.scale(scale)
        legScanHeightBias *= scale
        tridentRotationalKnockBack /= scale
    }

    var stationary = LerpGait(
        bodyHeight = 1.1,
        triggerZone = SplitDistance(.25, 1.5)
    )

    var moving = LerpGait(
        bodyHeight = 1.1,
        triggerZone = SplitDistance(.8,1.5)
    )

    var maxSpeed = walkSpeed
    var moveAcceleration = .15 / 4

    var rotateAcceleration = .15f / 4
    var rotationalDragCoefficient = .2f

    var legMoveSpeed = walkSpeed * 3

    var legLiftHeight = .35
    var legDropDistance = legLiftHeight

    var comfortZone = SplitDistance(1.2, 1.6)

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
    var crossPairCooldown = 4

    var useLegacyNormalForce = false
    var polygonLeeway = .0
    
    // TODO: Consider removing this
    var stabilizationFactor = .0 //0.7

    var uncomfortableSpeedMultiplier = 0.0

    var disableAdvancedRotation = false
    var preferredPitchLeeway = toRadians(10f)

    var straightenLegs = true
    var legStraightenRotation = toRadians(-80f)

    var scanPivotMode = PivotMode.YAxis
    var legChainPivotMode = PivotMode.SpiderOrientation

    var preferLevelBreakpoint = toRadians(45f)
    var preferLevelBias = .2f
    var preferredRotationLerpFraction = .3f
}


enum class PivotMode(val get: (spider: Spider) -> Quaternionf) {
    YAxis({ spider -> Quaternionf().rotateY(spider.orientation.getEulerAnglesYXZ(org.joml.Vector3f()).y) }),
    SpiderOrientation({ spider -> spider.orientation }),
    GroundOrientation({ spider -> spider.preferredOrientation })
}