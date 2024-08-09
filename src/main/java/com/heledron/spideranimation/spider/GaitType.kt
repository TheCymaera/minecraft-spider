package com.heledron.spideranimation.spider

object WalkGaitType {
    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.body.legs.indexOf(leg)

        // always move if the target is not on ground
        if (!leg.target.isGrounded) return true

        leg.isPrimary = true

        // ensure adjacent legs are grounded
        // ignore if disabled
        // ignore if target is not grounded
        val adjacent = unIndexLeg(spider, LegLookUp.adjacent(index))
        if (adjacent.any { !it.isGrounded() && !it.isDisabled && it.target.isGrounded }) return false

        // cooldown
        val diagonal = unIndexLeg(spider, LegLookUp.diagonal(index))
        if (diagonal.any { hasCooldown(it, spider.gait.legWalkCooldown) }) return false

        val wantsToMove = leg.isOutsideTriggerZone || !leg.touchingGround
        val alreadyAtTarget = leg.endEffector.distanceSquared(leg.target.position) < 0.01
        val onGround = spider.body.legs.any { it.isGrounded() } || spider.body.onGround

        return wantsToMove && !alreadyAtTarget && onGround
    }
}

object GallopGaitType {
    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.body.legs.indexOf(leg)

        if (!spider.isWalking) return WalkGaitType.canMoveLeg(leg)

        // always move if the target is not on ground
        if (!leg.target.isGrounded) return true

        // only move when at least one leg is on the ground
        val onGround = spider.body.legs.any { it.isGrounded() } || spider.body.onGround
        if (!onGround) return false

        val pair = spider.body.legs[LegLookUp.horizontal(index)]

        leg.isPrimary = LegLookUp.isDiagonal1(index) || pair.isDisabled || !pair.target.isGrounded

        if (leg.isPrimary) {
            // cooldown
            val front = spider.body.legs.getOrNull(LegLookUp.diagonalFront(index))
            val back = spider.body.legs.getOrNull(LegLookUp.diagonalBack(index))
            if (listOfNotNull(front, back).any { hasCooldown(it, spider.gait.legGallopVerticalCooldown) }) return false

            return leg.isOutsideTriggerZone || !leg.touchingGround
        } else {
            return pair.isMoving && !hasCooldown(pair, spider.gait.legGallopHorizontalCooldown)
        }
    }
}

fun hasCooldown(leg: Leg, cooldown: Int): Boolean {
    return leg.isMoving && leg.target.isGrounded && leg.timeSinceBeginMove < cooldown
}

fun unIndexLeg(spider: Spider, indices: List<Int>): List<Leg> {
    return indices.mapNotNull { spider.body.legs.getOrNull(it) }
}
