package com.heledron.spideranimation.spider.body

object LegLookUp {
    fun diagonalPairs(legs: List<Int>): List<List<Int>> {
        return legs.map { diagonal(it) + it }
    }

    fun isLeftLeg(leg: Int): Boolean {
        return leg % 2 == 0
    }

    fun isRightLeg(leg: Int): Boolean {
        return !isLeftLeg(leg)
    }

    fun getPairIndex(leg: Int): Int {
        return leg / 2
    }

    fun isDiagonal1(leg: Int): Boolean {
        return if (getPairIndex(leg) % 2 == 0) isLeftLeg(leg) else isRightLeg(leg)
    }

    fun isDiagonal2(leg: Int): Boolean {
        return !isDiagonal1(leg)
    }

    fun diagonalFront(leg: Int): Int {
        return if (isLeftLeg(leg)) leg - 1 else leg - 3
    }

    fun diagonalBack(leg: Int): Int {
        return if (isLeftLeg(leg)) leg + 3 else leg + 1
    }

    fun front(leg: Int): Int {
        return leg - 2
    }

    fun back(leg: Int): Int {
        return leg + 2
    }

    fun horizontal(leg: Int): Int {
        return if (isLeftLeg(leg)) leg + 1 else leg - 1
    }

    fun diagonal(leg: Int): List<Int> {
        return listOf(diagonalFront(leg), diagonalBack(leg))
    }

    fun adjacent(leg: Int): List<Int> {
        return listOf(front(leg), back(leg), horizontal(leg))
    }
}