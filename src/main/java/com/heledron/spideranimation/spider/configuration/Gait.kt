package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.body.GaitType
import com.heledron.spideranimation.utilities.SplitDistance
import com.heledron.spideranimation.utilities.lerp
import com.heledron.spideranimation.utilities.toRadians


class Gait(
    var bodyHeight: Double = 1.1,
    var triggerZone: SplitDistance = SplitDistance(.25, 1.5)
) {
    fun scale(scale: Double): Gait {
        return Gait(
            bodyHeight = bodyHeight * scale,
            triggerZone = triggerZone.scale(scale)
        )
    }

    fun clone() = Gait(
        bodyHeight = bodyHeight,
        triggerZone = triggerZone
    )

    fun lerp(target: Gait, factor: Double): Gait {
        this.bodyHeight = bodyHeight.lerp(target.bodyHeight, factor)
        this.triggerZone = triggerZone.lerp(target.triggerZone, factor)
        return this
    }

    companion object {
        fun stationary() = Gait()

        fun movingButNotWalking() = stationary()/*.apply {
            triggerZone = SplitDistance(triggerZone.horizontal / 2, triggerZone.vertical)
        }*/

        fun walk() = stationary().apply {
            triggerZone = SplitDistance(.8,1.5)
        }

        fun gallop() = walk().apply {
            bodyHeight *= 1.3
        }
    }
}


class MoveGait(
    walkSpeed: Double,
    val type: GaitType,
) {
    companion object {

        fun defaultWalk() = MoveGait(.15, GaitType.WALK)

        fun defaultGallop() = MoveGait(.4, GaitType.GALLOP).apply {
            gait = Gait.gallop()
            legWalkCooldown = 1
            legMoveSpeed = .6
            rotateAcceleration = .25f / 4
            uncomfortableSpeedMultiplier = .6
        }
    }

    fun scale(scale: Double) {
        maxSpeed *= scale
        walkAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        legDropDistance *= scale
        comfortZone = comfortZone.scale(scale)
        legScanHeightBias *= scale
    }

    var gait = Gait.walk()

    var maxSpeed = walkSpeed
    var walkAcceleration = .15 / 4

    var rotateAcceleration = .15f / 4
    var rotationalDragCoefficient = .2f

    var legMoveSpeed = walkSpeed * 3

    var legLiftHeight = .35
    var legDropDistance = legLiftHeight

//    var stationaryTriggerZone = SplitDistance(.25,1.5)
//    var movingTriggerZone = SplitDistance(.8,1.5)
    var comfortZone = SplitDistance(1.2, 1.6)

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

//    var bodyHeight = 1.1

    var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    var bodyHeightCorrectionFactor = .25

    var legScanAlternativeGround = true
    var legScanHeightBias = .5

    var tridentKnockBack = .3
    var legLookAheadFraction = .6
    var groundDragCoefficient = .2

    var legWalkCooldown = 2
    var legGallopHorizontalCooldown = 1
    var legGallopVerticalCooldown = 4

    var useLegacyNormalForce = false
    var polygonLeeway = .0
    
    // TODO: Consider removing this
    var stabilizationFactor = .0 //0.7

    var uncomfortableSpeedMultiplier = 0.0

    var disableAdvancedRotation = false

    var straightenLegs = true
    var legStraightenRotation = toRadians(-80f)
}