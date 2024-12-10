package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import org.bukkit.entity.Trident
import org.bukkit.util.Vector
import org.joml.Vector2d
import kotlin.math.*

class SpiderBody(val spider: Spider): SpiderComponent {
    val onHitGround = EventEmitter()
    var onGround = false; private set
    var legs = spider.options.bodyPlan.legs.map { Leg(spider, it) }
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector(0.0, 0.0, 0.0); private set
    val onGetHitByTrident = EventEmitter()

    override fun update() {
        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / spider.body.legs.size

        // apply gravity and air resistance
        spider.velocity.y -= spider.gait.gravityAcceleration
        spider.velocity.y *= (1 - spider.gait.airDragCoefficient)

        // apply ground drag
        if (!spider.isWalking) {
            val drag = spider.gait.groundDragCoefficient * fractionOfLegsGrounded
            spider.velocity.x *= drag
            spider.velocity.z *= drag
        }

        if (onGround) {
            spider.velocity.x *= .5
            spider.velocity.z *= .5
        }


        normal = getNormal(spider)

        normalAcceleration = Vector(0.0, 0.0, 0.0)
        normal?.let {
            val preferredY = getPreferredY()
            val preferredYAcceleration = (preferredY - spider.position.y - spider.velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = spider.gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = it.normal.clone().multiply(accelerationMagnitude)

            // if the horizontal acceleration is too high,
            // there's no point accelerating as the spider will fall over anyway
            if (horizontalLength(normalAcceleration) > normalAcceleration.y) normalAcceleration.multiply(0.0)

            spider.velocity.add(normalAcceleration)
        }

        // apply velocity
        spider.position.add(spider.velocity)

        // resolve collision
        val collision = spider.world.resolveCollision(spider.position, Vector(0.0, min(-1.0, -abs(spider.velocity.y)), 0.0))
        if (collision != null) {
            onGround = true

            val didHit = collision.offset.length() > (spider.gait.gravityAcceleration * 2) * (1 - spider.gait.airDragCoefficient)
            if (didHit) onHitGround.emit()

            spider.position.y = collision.position.y
            if (spider.velocity.y < 0) spider.velocity.y *= -spider.gait.bounceFactor
            if (spider.velocity.y < spider.gait.gravityAcceleration) spider.velocity.y = .0
        } else {
            onGround = spider.world.isOnGround(spider.position, DOWN_VECTOR.rotate(spider.orientation))
        }

        val updateOrder = getLegsInUpdateOrder(spider)
        for (leg in updateOrder) leg.updateMemo()
        for (leg in updateOrder) leg.update()


        val tridents = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), 1.5, 1.5, 1.5) {
            it is Trident && it.shooter != spider.mount.getRider()
        }
        for (trident in tridents) {
            if (trident != null && trident.velocity.length() > 2.0) {
                val tridentDirection = trident.velocity.normalize()

                trident.velocity = tridentDirection.clone().multiply(-.3)
                onGetHitByTrident.emit()

                spider.velocity.add(tridentDirection.multiply(spider.gait.tridentKnockBack))
            }
        }
    }

    private fun getPreferredY(): Double {
        val averageY = spider.body.legs.map { it.target.position.y }.average() + spider.gait.bodyHeight
        val targetY = averageY //max(averageY, groundY)
        val stabilizedY = spider.position.y.lerp(targetY, spider.gait.bodyHeightCorrectionFactor)
        return stabilizedY
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
    }

    private fun getLegsInUpdateOrder(spider: Spider): List<Leg> {
        val diagonal1 = legs.indices.filter { LegLookUp.isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { LegLookUp.isDiagonal2(it) }
        val indices = diagonal1 + diagonal2
        return indices.map { spider.body.legs[it] }
    }

    private fun getLegacyNormal(): NormalInfo? {
        val pairs = LegLookUp.diagonalPairs(legs.indices.toList())
        if (pairs.any { pair -> pair.mapNotNull { spider.body.legs.getOrNull(it) }.all { it.isGrounded() } }) {
            return NormalInfo(normal = Vector(0, 1, 0))
        }

        return null
    }

    private fun getNormal(spider: Spider): NormalInfo? {
        if (spider.gait.useLegacyNormalForce) return getLegacyNormal()

        val centreOfMass = averageVector(spider.body.legs.map { it.endEffector })
        centreOfMass.lerp(spider.position, 0.5)
        centreOfMass.y += 0.01

        val groundedLegs = legsInPolygonalOrder().map { spider.body.legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        val legsPolygon = groundedLegs.map { it.endEffector.clone() }
        val polygonCenterY = legsPolygon.map { it.y }.average()

        // only 1 leg on ground
        if (legsPolygon.size == 1) {
            val origin = groundedLegs.first().endEffector.clone()
            return NormalInfo(
                normal = centreOfMass.clone().subtract(origin).normalize(),
                origin = origin,
                centreOfMass = centreOfMass,
                contactPolygon = legsPolygon
            ).apply { applyStabilization(this) }
        }

        val polygon2D = legsPolygon.map { Vector2d(it.x, it.z) }

        // inside polygon. accelerate upwards towards centre of mass
        if (pointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)) return NormalInfo(
            normal = Vector(0, 1, 0),
            origin = Vector(centreOfMass.x, polygonCenterY, centreOfMass.z),
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon
        )

        // outside polygon, accelerate at an angle from within the polygon
        val point = nearestPointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)
        val origin = Vector(point.x, polygonCenterY, point.y)
        return NormalInfo(
            normal = centreOfMass.clone().subtract(origin).normalize(),
            origin = origin,
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon
        ).apply { applyStabilization(this )}
    }

    private fun applyStabilization(normal: NormalInfo) {
        if (normal.origin == null) return
        if (normal.centreOfMass == null) return

        if (horizontalDistance(normal.origin, normal.centreOfMass) < spider.gait.polygonLeeway) {
            normal.origin.x = normal.centreOfMass.x
            normal.origin.z = normal.centreOfMass.z
        }

        val stabilizationTarget = normal.origin.clone().setY(normal.centreOfMass.y)
        normal.centreOfMass.lerp(stabilizationTarget, spider.gait.stabilizationFactor)

        normal.normal.copy(normal.centreOfMass).subtract(normal.origin).normalize()
    }
}

class NormalInfo(
    // most of these fields are only used for debug rendering
    val normal: Vector,
    val origin: Vector? = null,
    val contactPolygon: List<Vector>? = null,
    val centreOfMass: Vector? = null
)