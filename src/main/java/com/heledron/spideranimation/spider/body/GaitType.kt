package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.spider.Spider

enum class GaitType(val canMoveLeg: (Leg) -> Boolean, val getLegsInUpdateOrder: (Spider) -> List<Leg>) {
    WALK(WalkGaitType::canMoveLeg, WalkGaitType::getLegsInUpdateOrder),
    GALLOP(GallopGaitType::canMoveLeg, GallopGaitType::getLegsInUpdateOrder)
}

object WalkGaitType {
    fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        val legs = spider.body.legs
        val diagonal1 = legs.indices.filter { LegLookUp.isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { LegLookUp.isDiagonal2(it) }
        val indices = diagonal1 + diagonal2
        return indices.map { spider.body.legs[it] }
    }

    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.body.legs.indexOf(leg)

        // always move if the target is not on ground
        if (!leg.target.isGrounded) return true

        leg.isPrimary = true

        // ensure other pair is grounded
        // ignore if disabled, ignore if target is not grounded
        val crossPair = unIndexLeg(spider, LegLookUp.adjacent(index))
        if (crossPair.any { !it.isGrounded() && !it.isDisabled && it.target.isGrounded }) return false

        // cooldown
        if (crossPair.any { it.target.isGrounded && it.timeSinceStopMove < spider.gait.crossPairCooldown }) return false
        val samePair = unIndexLeg(spider, LegLookUp.diagonal(index))
        if (samePair.any { it.target.isGrounded && it.timeSinceBeginMove < spider.gait.samePairCooldown }) return false

        val wantsToMove = leg.isOutsideTriggerZone || !leg.touchingGround
        val alreadyAtTarget = leg.endEffector.distanceSquared(leg.target.position) < 0.01
        val onGround = spider.body.legs.any { it.isGrounded() } || spider.body.onGround

        return wantsToMove && !alreadyAtTarget && onGround
    }
}

object GallopGaitType {
    fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        return WalkGaitType.getLegsInUpdateOrder(spider)
//        val legs = spider.body.legs
//        val diagonal1 = legs.indices.filter { LegLookUp.isDiagonal1(it) }
//        val diagonal2 = legs.indices.filter { LegLookUp.isDiagonal2(it) }
//
//        val indices = diagonal1.zip(diagonal2).flatMap { (a, b) -> listOf(a, b) }
//        return indices.map { spider.body.legs[it] }
    }

    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.body.legs.indexOf(leg)

        if (!spider.isWalking) return WalkGaitType.canMoveLeg(leg)

//        if (spider.velocity.length() < spider.gait.maxSpeed * 0.5) return WalkGaitType.canMoveLeg(leg)

        // always move if the target is not on ground
        if (!leg.target.isGrounded) return true

        // only move when at least one leg is on the ground
        val onGround = spider.body.legs.any { it.isGrounded() } || spider.body.onGround
        if (!onGround) return false

        val pair = spider.body.legs[LegLookUp.horizontal(index)]

        leg.isPrimary = LegLookUp.isDiagonal1(index) || pair.isDisabled || !pair.target.isGrounded

        if (leg.isPrimary) {
            // check cooldown
            val front = spider.body.legs.getOrNull(LegLookUp.diagonalFront(index))
            val back = spider.body.legs.getOrNull(LegLookUp.diagonalBack(index))
            if (listOfNotNull(front, back).any { leg.target.isGrounded && leg.timeSinceBeginMove < spider.gait.crossPairCooldown }) return false

            return leg.isOutsideTriggerZone || !leg.touchingGround
        } else {
            val pairHasCooldown = (pair.target.isGrounded && pair.timeSinceBeginMove < spider.gait.samePairCooldown)
            return pair.isMoving && !pairHasCooldown
        }
    }
}

//fun hasCooldown(leg: Leg, cooldown: Int): Boolean {
//    return /*leg.isMoving && */leg.target.isGrounded && leg.timeSinceBeginMove < cooldown
//}

fun unIndexLeg(spider: Spider, indices: List<Int>): List<Leg> {
    return indices.mapNotNull { spider.body.legs.getOrNull(it) }
}