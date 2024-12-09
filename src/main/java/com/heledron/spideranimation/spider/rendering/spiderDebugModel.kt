package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.utilities.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Vector3d


fun spiderDebugModel(spider: Spider): Model {
    val model = Model()

    val scale = spider.options.bodyPlan.scale.toFloat()

    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        // Render scan bars
        if (spider.options.debug.scanBars) model.add(Pair("scanBar", legIndex), lineModel(
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
        if (spider.options.debug.triggerZones) model.add(Pair("triggerZoneVertical", legIndex), lineModel(
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
        if (spider.options.debug.triggerZones) model.add(Pair("triggerZoneHorizontal", legIndex), blockModel(
            location = run {
                val location = leg.triggerZoneCenter.toLocation(leg.spider.location.world!!)
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
        if (spider.options.debug.endEffectors) model.add(Pair("endEffector", legIndex), blockModel(
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
        if (spider.options.debug.targetPositions) model.add(Pair("targetPosition", legIndex), blockModel(
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
    if (spider.options.debug.orientation) {
        val forward = Vector(0.0, 0.0, scale.toDouble()).rotate(spider.orientation())

        model.add("direction", blockModel(
            location = spider.location.clone().add(forward),
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
    }

    // Render preferred orientation
    if (spider.options.debug.preferredOrientation) {
        val forward = Vector(0.0, 0.0, 2.0).rotate(spider.preferredOrientation())

        model.add("preferredOrientation", lineModel(
            location = Location(spider.location.world, spider.location.x, spider.location.y, spider.location.z),
            vector = forward,
            thickness = .05f * scale,
            init = {
                it.block = Material.BLUE_STAINED_GLASS.createBlockData()
            }
        ))

    }


    val normal = spider.body.normal ?: return model
    if (spider.options.debug.legPolygons && normal.contactPolygon != null) {
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

    if (spider.options.debug.centreOfMass && normal.centreOfMass != null) model.add("centreOfMass", blockModel(
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


    if (spider.options.debug.normalForce && normal.centreOfMass != null && normal.origin !== null) model.add("acceleration", lineModel(
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