package com.heledron.spideranimation.spider

import com.heledron.spideranimation.utilities.*
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.util.Vector

fun targetModel(
    location: Location
) = blockModel(
    location = location,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(.25f, .25f, .25f)
    }
)

private val DEFAULT_MATERIAL = Material.NETHERITE_BLOCK

fun spiderModel(spider: Spider): Model {
    val scale = spider.bodyPlan.storedScale

    val model = Model()

    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        val chain = leg.chain
        val maxThickness = 1.5/16 * 4 * scale * spider.bodyPlan.renderSegmentThickness
        val minThickness = 1.5/16 * 1 * scale * spider.bodyPlan.renderSegmentThickness

        // up vector is the cross product of the y-axis and the end-effector direction
        fun segmentUpVector(): Vector {
            val direction = chain.getEndEffector().clone().subtract(chain.root)
            return direction.clone().crossProduct(Vector(0, 1, 0))
        }

        val segmentUpVector = segmentUpVector()

        // Render leg segment
        for ((segmentIndex, segment) in chain.segments.withIndex()) {
            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root
            val vector = segment.position.clone().subtract(parent).normalize().multiply(segment.length)
            val location = parent.toLocation(spider.location.world!!)

            val thickness = (chain.segments.size - segmentIndex - 1) * (maxThickness - minThickness) / chain.segments.size + minThickness

            model.add(Pair(legIndex, segmentIndex), lineModel(
                location = location,
                vector = vector,
                thickness = thickness.toFloat(),
                upVector = segmentUpVector,
                update = {
                    val cloak = spider.cloak.getSegment(legIndex to segmentIndex)
                    it.block =  cloak ?: DEFAULT_MATERIAL.createBlockData()
                }
            ))
        }
    }

    return model
}


fun spiderDebugModel(spider: Spider): Model {
    val model = Model()

    val scale = spider.bodyPlan.storedScale.toFloat()

    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        // Render scan bars
        if (spider.debugOptions.scanBars) model.add(Pair("scanBar", legIndex), lineModel(
            location = leg.scanStartPosition.toLocation(spider.location.world!!),
            vector = leg.scanVector,
            thickness = .05f * scale,
            init = {
                it.brightness = Display.Brightness(15, 15)
            },
            update = {
                val material = if (leg.isPrimary) Material.GOLD_BLOCK else Material.IRON_BLOCK
                it.block = material.createBlockData()
            }
        ))

        // Render trigger zone
        val vector = Vector(0,1,0).multiply(leg.triggerZone.vertical)
        if (spider.debugOptions.triggerZones) model.add(Pair("triggerZoneVertical", legIndex), lineModel(
            location = leg.restPosition.toLocation(spider.location.world!!).subtract(vector.clone().multiply(.5)),
            vector = vector,
            thickness = .07f * scale,
            init = { it.brightness = Display.Brightness(15, 15) },
            update = {
                val material = if (leg.isUncomfortable) Material.RED_STAINED_GLASS else Material.CYAN_STAINED_GLASS
                it.block = material.createBlockData()
            }
        ))

        // Render trigger zone
        if (spider.debugOptions.triggerZones) model.add(Pair("triggerZoneHorizontal", legIndex), blockModel(
            location = run {
                val location = leg.restPosition.toLocation(leg.spider.location.world!!)
                location.y = leg.target.position.y.coerceIn(location.y - leg.triggerZone.vertical, location.y + leg.triggerZone.vertical)
                location
            },
            init = {
                it.teleportDuration = 1
                it.interpolationDuration = 1
                it.brightness = Display.Brightness(15, 15)
            },
            update = {
                val material = if (leg.isUncomfortable) Material.RED_STAINED_GLASS else Material.CYAN_STAINED_GLASS
                it.block = material.createBlockData()

                val size = 2 * leg.triggerZone.horizontal.toFloat()
                val ySize = 0.02f
                it.transformation = centredTransform(size, ySize, size)
            }
        ))

        // Render end effector
        if (spider.debugOptions.endEffectors) model.add(Pair("endEffector", legIndex), blockModel(
            location = leg.endEffector.toLocation(spider.location.world!!),
            init = {
                it.teleportDuration = 1
                it.brightness = Display.Brightness(15, 15)
            },
            update = {
                val size = (if (leg == spider.pointDetector.selectedLeg) .2f else .15f) * scale
                it.transformation = centredTransform(size, size, size)
                it.block = when {
                    leg.isDisabled -> Material.BLACK_CONCRETE.createBlockData()
                    leg.isGrounded() -> Material.DIAMOND_BLOCK.createBlockData()
                    leg.touchingGround -> Material.LAPIS_BLOCK.createBlockData()
                    else -> Material.REDSTONE_BLOCK.createBlockData()
                }
            }
        ))

        // Render target position
        if (spider.debugOptions.targetPositions) model.add(Pair("targetPosition", legIndex), blockModel(
            location = leg.target.position.toLocation(spider.location.world!!),
            init = {
                it.teleportDuration = 1
                it.brightness = Display.Brightness(15, 15)

                val size = 0.2f * scale
                it.transformation = centredTransform(size, size, size)
            },
            update = {
                val material = if (leg.target.isGrounded) Material.LIME_STAINED_GLASS else Material.RED_STAINED_GLASS
                it.block = material.createBlockData()
            }
        ))
    }

    // Render spider direction
    if (spider.debugOptions.direction) model.add("direction", blockModel(
        location = spider.location.clone().add(spider.location.direction.clone().multiply(scale)),
        init = {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f * scale
            it.transformation = centredTransform(size, size, size)
        },
        update = {
            it.block = if (spider.gait.gallop) Material.REDSTONE_BLOCK.createBlockData() else Material.EMERALD_BLOCK.createBlockData()
        }
    ))


    val normal = spider.body.normal ?: return model
    if (spider.debugOptions.legPolygons && normal.contactPolygon != null) {
        val points = normal.contactPolygon.map { it.toLocation(spider.location.world!!)}
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]

            model.add(Pair("polygon",i), lineModel(
                location = a,
                vector = b.toVector().subtract(a.toVector()),
                thickness = .05f * scale,
                interpolation = 0,
                init = { it.brightness = Display.Brightness(15, 15) },
                update = { it.block = Material.EMERALD_BLOCK.createBlockData() }
            ))
        }
    }

    if (spider.debugOptions.centreOfMass && normal.centreOfMass != null) model.add("centreOfMass", blockModel(
        location = normal.centreOfMass.toLocation(spider.location.world!!),
        init = {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f * scale
            it.transformation = centredTransform(size, size, size)
        },
        update = {
            val material = if (horizontalLength(normal.normal) == .0) Material.LAPIS_BLOCK else Material.REDSTONE_BLOCK
            it.block = material.createBlockData()
        }
    ))


    if (spider.debugOptions.normalForce && normal.centreOfMass != null && normal.origin !== null) model.add("acceleration", lineModel(
        location = normal.origin.toLocation(spider.location.world!!),
        vector = normal.centreOfMass.clone().subtract(normal.origin),
        thickness = .02f * scale,
        interpolation = 1,
        init = { it.brightness = Display.Brightness(15, 15) },
        update = {
            val material = if (spider.body.normalAcceleration.isZero) Material.BLACK_CONCRETE else Material.WHITE_CONCRETE
            it.block = material.createBlockData()
        }
    ))

    return model
}