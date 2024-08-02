package com.heledron.spideranimation

import com.heledron.spideranimation.components.LegPlan
import com.heledron.spideranimation.components.SegmentPlan
import com.heledron.spideranimation.components.SymmetricalBodyPlan
import org.bukkit.util.Vector

//fun gaitToMap(gait: Gait): Map<String, Any?> {
//    return KVElements.toMap(gait)
//}
//
//fun setGaitValue(gait: Gait, property: String, value: Any?) {
//    KVElements.set(gait, property, value)
//}
//
//fun gaitFromMap(gait: Gait, map: Map<String, Any>) {
//    for ((key, value) in map) KVElements.set(gait, key, value)
//}

fun bodyPlanFromMap(map: Map<String, Any>): SymmetricalBodyPlan {
    if (map["kind"] != "symmetrical_pair") error("BodyPlan.kind must be \"symmetrical_pair\", received \"${map["kind"]}\"")

    val legsMap = map["legs"] as? List<Map<String, Any>> ?: error("BodyPlan.legs must be a list of LegBuildPlan")

    val legs = legsMap.map { legPlanFromMap(it) }
    if (legs.isEmpty()) error("A symmetrical pair must have at least one leg")

    return SymmetricalBodyPlan(legs.toMutableList())
}

fun mapFromBodyPlan(bodyPlan: SymmetricalBodyPlan): Map<String, Any> {
    val legs = bodyPlan.legs.map { mapFromLegPlan(it) }

    return mapOf(
        "kind" to "symmetrical_pair",
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