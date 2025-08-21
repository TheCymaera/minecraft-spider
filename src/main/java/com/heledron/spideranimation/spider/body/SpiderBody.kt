package com.heledron.spideranimation.spider.body

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.spider.SpiderComponent
import com.heledron.spideranimation.utilities.*
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector2d
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpiderBody(val spider: Spider): SpiderComponent {
    val onHitGround = EventEmitter()
    var onGround = false; private set
    var legs = spider.options.bodyPlan.legs.map { Leg(spider, it) }
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector(0.0, 0.0, 0.0); private set

    override fun update() {
        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / spider.body.legs.size

        // apply gravity and air resistance
        spider.velocity.y -= spider.gait.gravityAcceleration
        spider.velocity.y *= (1 - spider.gait.airDragCoefficient)

        // apply rotational velocity
        val rotVelocity = Quaternionf().rotationYXZ(spider.rotationalVelocity.y, spider.rotationalVelocity.x, spider.rotationalVelocity.z)
        spider.orientation.set(rotVelocity.mul(spider.orientation))

        // apply drag while leg on ground
        if (!spider.isWalking) {
            val legDrag = 1 - spider.gait.groundDragCoefficient * fractionOfLegsGrounded
            spider.velocity.x *= legDrag
            spider.velocity.z *= legDrag
        }

        // apply rotational drag
        val rotDrag = 1 - spider.gait.rotationalDragCoefficient * fractionOfLegsGrounded.toFloat()
        spider.rotationalVelocity.mul(rotDrag)

        // apply drag while body on ground
        if (onGround) {
            val bodyDrag = .5f
            spider.velocity.x *= bodyDrag
            spider.velocity.z *= bodyDrag

            spider.rotationalVelocity.mul(bodyDrag)
        }

        val normal = getNormal(spider)
        this.normal = normal

        normalAcceleration = Vector(0.0, 0.0, 0.0)
        if (normal != null) {
            val preferredY = getPreferredY()
            val preferredYAcceleration = (preferredY - spider.position.y - spider.velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = spider.gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = normal.normal.clone().multiply(accelerationMagnitude)

            // if the horizontal acceleration is too high,
            // there's no point accelerating as the spider will fall over anyway
            if (normalAcceleration.horizontalLength() > normalAcceleration.y) normalAcceleration.multiply(0.0)

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

        val updateOrder = spider.gait.type.getLegsInUpdateOrder(spider)
        for (leg in updateOrder) leg.updateMemo()
        for (leg in updateOrder) leg.update()
    }

    private fun getPreferredY(): Double {
        val lookAhead = spider.position.clone().add(spider.velocity)
        val ground = spider.world.raycastGround(lookAhead, DOWN_VECTOR.rotate(spider.preferredOrientation), spider.lerpedGait.bodyHeight)
        val groundY = ground?.hitPosition?.y ?: -Double.MAX_VALUE

        val averageY = spider.body.legs.map { it.target.position.y }.average() + spider.lerpedGait.bodyHeight

        val pivot = spider.gait.legChainPivotMode.get(spider)
        val target = UP_VECTOR.rotate(pivot).multiply(spider.gait.maxBodyDistanceFromGround)
        val targetY = max(averageY, groundY + target.y)
        val stabilizedY = spider.position.y.lerp(targetY, spider.gait.bodyHeightCorrectionFactor)

        return stabilizedY
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
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

        val centreOfMass = spider.body.legs.map { it.endEffector }.average()
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

        if (normal.origin.horizontalDistance(normal.centreOfMass) < spider.gait.polygonLeeway) {
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