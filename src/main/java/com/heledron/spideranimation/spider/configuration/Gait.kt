package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.utilities.SplitDistance


class Gait(
    walkSpeed: Double,
    gallop: Boolean,
) {
    companion object {
        fun defaultWalk(): Gait {
            return Gait(.15, false)
        }

        fun defaultGallop(): Gait {
            return Gait(.4, true).apply {
                legWalkCooldown = 1
                legMoveSpeed = .6
                rotateSpeed = .25
                uncomfortableSpeedMultiplier = .6
            }
        }
    }

    fun scale(scale: Double) {
        walkSpeed *= scale
        walkAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        legDropDistance *= scale
        stationaryTriggerZone = stationaryTriggerZone.scale(scale)
        walkingTriggerZone = walkingTriggerZone.scale(scale)
        comfortZone = comfortZone.scale(scale)
        bodyHeight *= scale
        legScanHeightBias *= scale
    }

    var gallop = gallop

    var walkSpeed = walkSpeed
    var walkAcceleration = .15 / 4

    var rotateSpeed = .15

    var legMoveSpeed = walkSpeed * 3

    var legLiftHeight = .35
    var legDropDistance = legLiftHeight

    var stationaryTriggerZone = SplitDistance(.25,1.5)
    var walkingTriggerZone = SplitDistance(.8,1.5)
    var comfortZone = SplitDistance(1.2, 1.6)

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

    var bodyHeight = 1.1

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
    var stabilizationFactor = 0.7

    var uncomfortableSpeedMultiplier = 0.0

    var disableAdvancedRotation = false
}