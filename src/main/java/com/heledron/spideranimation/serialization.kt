package com.heledron.spideranimation

import org.bukkit.util.Vector

fun gaitToMap(gait: Gait): Map<String, Any> {
    val map = mutableMapOf<String, Any>()

    map["walkSpeed"] = gait.walkSpeed
    map["walkAcceleration"] = gait.walkAcceleration
    map["rotateSpeed"] = gait.rotateSpeed

    map["gallopBreakpoint"] = gait.gallopBreakpoint

    map["legMoveSpeed"] = gait.legMoveSpeed
    map["legLiftHeight"] = gait.legLiftHeight
    map["legDropDistance"] = gait.legDropDistance

    map["legVerticalTriggerDistance"] = gait.legVerticalTriggerDistance
    map["legVerticalDiscomfortDistance"] = gait.legVerticalDiscomfortDistance

    map["legStationaryTriggerDistance"] = gait.legStationaryTriggerDistance
    map["legWalkingTriggerDistance"] = gait.legWalkingTriggerDistance
    map["legDiscomfortDistance"] = gait.legDiscomfortDistance

    map["gravityAcceleration"] = gait.gravityAcceleration
    map["airDragCoefficient"] = gait.airDragCoefficient
    map["bounceFactor"] = gait.bounceFactor

    map["bodyHeightCorrectionAcceleration"] = gait.bodyHeightCorrectionAcceleration
    map["bodyHeightCorrectionFactor"] = gait.bodyHeightCorrectionFactor
    map["bodyHeight"] = gait.bodyHeight

    map["legScanAlternativeGround"] = gait.legScanAlternativeGround
    map["legScanHeightBias"] = gait.legScanHeightBias

    map["legStraightenRotation"] = gait.legStraightenRotation
    map["legStraightenMinRotation"] = gait.legStraightenMinRotation
    map["legStraightenMaxRotation"] = gait.legStraightenMaxRotation
    map["legNoStraighten"] = gait.legNoStraighten

    map["tridentKnockBack"] = gait.tridentKnockBack
    map["legLookAheadFraction"] = gait.legLookAheadFraction

    map["legMoveCooldown"] = gait.legMoveCooldown
    map["gallopSpeed"] = gait.gallopBreakpoint

    map["stabilizationFactor"] = gait.stabilizationFactor

    return map
}

fun setGaitValue(gait: Gait, property: String, value: Any) {
    when (property) {
        "walkSpeed" -> gait.walkSpeed = value as? Double ?: return
        "walkAcceleration" -> gait.walkAcceleration = value as? Double ?: return
        "rotateSpeed" -> gait.rotateSpeed = value as? Double ?: return

        "gallopBreakpoint" -> gait.gallopBreakpoint = value as? Double ?: return

        "legMoveSpeed" -> gait.legMoveSpeed = value as? Double ?: return
        "legLiftHeight" -> gait.legLiftHeight = value as? Double ?: return
        "legDropDistance" -> gait.legDropDistance = value as? Double ?: return

        "legVerticalTriggerDistance" -> gait.legVerticalTriggerDistance = value as? Double ?: return
        "legVerticalDiscomfortDistance" -> gait.legVerticalDiscomfortDistance = value as? Double ?: return

        "legStationaryTriggerDistance" -> gait.legStationaryTriggerDistance = value as? Double ?: return
        "legWalkingTriggerDistance" -> gait.legWalkingTriggerDistance = value as? Double ?: return
        "legDiscomfortDistance" -> gait.legDiscomfortDistance = value as? Double ?: return

        "gravityAcceleration" -> gait.gravityAcceleration = value as? Double ?: return
        "airDragCoefficient" -> gait.airDragCoefficient = value as? Double ?: return
        "bounceFactor" -> gait.bounceFactor = value as? Double ?: return

        "bodyHeightCorrectionAcceleration" -> gait.bodyHeightCorrectionAcceleration = value as? Double ?: return
        "bodyHeightCorrectionFactor" -> gait.bodyHeightCorrectionFactor = value as? Double ?: return
        "bodyHeight" -> gait.bodyHeight = value as? Double ?: return

        "legScanAlternativeGround" -> gait.legScanAlternativeGround = value as? Boolean ?: return
        "legScanHeightBias" -> gait.legScanHeightBias = value as? Double ?: return

        "legStraightenRotation" -> gait.legStraightenRotation = value as? Double ?: return
        "legStraightenMinRotation" -> gait.legStraightenMinRotation = value as? Double ?: return
        "legStraightenMaxRotation" -> gait.legStraightenMaxRotation = value as? Double ?: return
        "legNoStraighten" -> gait.legNoStraighten = value as? Boolean ?: return

        "tridentKnockBack" -> gait.tridentKnockBack = value as? Double ?: return
        "legLookAheadFraction" -> gait.legLookAheadFraction = value as? Double ?: return

        "legMoveCooldown" -> gait.legMoveCooldown = value as? Int ?: return
        "gallopSpeed" -> gait.gallopBreakpoint = value as? Double ?: return

        "stabilizationFactor" -> gait.stabilizationFactor = value as? Double ?: return
    }
}

fun gaitFromMap(gait: Gait, map: Map<String, Any>) {
    for ((key, value) in map) {
        setGaitValue(gait, key, value)
    }
}

fun bodyPlanFromMap(map: Map<String, Any>): SymmetricalBodyPlanBuilder {
    if (map["kind"] != "symmetrical_pair") error("BodyPlan.kind must be \"symmetrical_pair\", received \"${map["kind"]}\"")

    val legsMap = map["legs"] as? List<Map<String, Any>> ?: error("BodyPlan.legs must be a list of LegBuildPlan")

    val legs = legsMap.map { legPlanFromMap(it) }
    if (legs.isEmpty()) error("A symmetrical pair must have at least one leg")

    val scale = map["scale"] as? Double ?: 1.0

    return SymmetricalBodyPlanBuilder(scale, legs.toMutableList())
}

fun mapFromBodyPlan(bodyPlan: SymmetricalBodyPlanBuilder): Map<String, Any> {
    val legs = bodyPlan.legs.map { mapFromLegPlan(it) }

    return mapOf(
        "kind" to "symmetrical_pair",
        "scale" to bodyPlan.scale,
        "legs" to legs
    )
}

fun legPlanFromMap(map: Map<String, Any>): LegPlan {
    val attachmentPosition = map["attachment_position"] as? Vector ?: error("LegPlan.attachment_position must be a Vector")
    val restPosition = map["rest_position"] as? Vector ?: error("LegPlan.rest_position must be a Vector")
    val segmentsMap = map["segments"] as? List<Map<String, Any>> ?: error("LegPlan.segments must be a list of SegmentPlan")

    val segments = segmentsMap.map { segmentPlanFromMap(it) }

    return LegPlan(attachmentPosition, restPosition, segments.toMutableList())
}

fun mapFromLegPlan(legPlan: LegPlan): Map<String, Any> {
    return mapOf(
        "attachment_position" to legPlan.attachmentPosition,
        "rest_position" to legPlan.restPosition,
        "segments" to legPlan.segments.map { mapFromSegmentPlan(it) }
    )
}

fun segmentPlanFromMap(map: Map<String, Any>): SegmentPlan {
    val length = map["length"] as? Double ?: error("SegmentPlan.length must be a Double")
    val thickness = map["thickness"] as? Double ?: error("SegmentPlan.thickness must be a Double")
    return SegmentPlan(length, thickness)
}

fun mapFromSegmentPlan(segmentPlan: SegmentPlan): Map<String, Any> {
    return mapOf(
        "length" to segmentPlan.length,
        "thickness" to segmentPlan.thickness
    )
}