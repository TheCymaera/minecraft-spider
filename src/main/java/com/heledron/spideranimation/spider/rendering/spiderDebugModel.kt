package com.heledron.spideranimation.spider.rendering

import com.heledron.spideranimation.spider.Spider
import com.heledron.spideranimation.utilities.*
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f


fun spiderDebugModel(spider: Spider): Model {
    val model = Model()

    val scale = spider.options.bodyPlan.scale.toFloat()

    for ((legIndex, leg) in spider.body.legs.withIndex()) {
        // Render scan bars
        if (spider.options.debug.scanBars) model.add(Pair("scanBar", legIndex), lineModel(
            world = spider.world,
            position = leg.scanStartPosition,
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
        if (spider.options.debug.triggerZones) model.add(Pair("triggerZoneVertical", legIndex), blockModel(
            world = spider.world,
            position = leg.triggerZone.center,
            init = {
                it.teleportDuration = 1
                it.interpolationDuration = 1
                it.brightness = Display.Brightness(15, 15)
            },
            update = {
                val material = if (leg.isUncomfortable) Material.RED_STAINED_GLASS else Material.CYAN_STAINED_GLASS
                it.block = material.createBlockData()

                val thickness = .07f * scale
                val transform = Matrix4f()
                    .rotate(spider.preferredOrientation)
                    .scale(thickness, 2 * leg.triggerZone.vertical.toFloat(), thickness)
                    .translate(-.5f,-.5f,-.5f)

                it.applyTransformationWithInterpolation(transform)
            }
        ))

        // Render trigger zone
        if (spider.options.debug.triggerZones) model.add(Pair("triggerZoneHorizontal", legIndex), blockModel(
            world = spider.world,
            position = run {
                val pos = leg.triggerZone.center.clone()
                pos.y = leg.target.position.y.coerceIn(pos.y - leg.triggerZone.vertical, pos.y + leg.triggerZone.vertical)
                pos
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
                val transform = Matrix4f()
                    .rotate(spider.preferredOrientation)
                    .scale(size, ySize, size)
                    .translate(-.5f,-.5f,-.5f)

                it.applyTransformationWithInterpolation(transform)
            }
        ))

        // Render end effector
        if (spider.options.debug.endEffectors) model.add(Pair("endEffector", legIndex), blockModel(
            world = spider.world,
            position = leg.endEffector,
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
            location = leg.target.position.toLocation(spider.world),
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
    if (spider.options.debug.orientation) model.add("direction", blockModel(
        location = spider.position.toLocation(spider.world),
        init = {
            it.teleportDuration = 1
            it.interpolationDuration = 1
            it.brightness = Display.Brightness(15, 15)
        },
        update = {
            it.block = if (spider.gallop) Material.REDSTONE_BLOCK.createBlockData() else Material.EMERALD_BLOCK.createBlockData()

            val size = .1f * scale
            val displacement = 1f * scale
            val transform = Matrix4f()
                .rotate(spider.orientation)
                .translate(FORWARD_VECTOR.toVector3f().mul(displacement))
                .scale(size, size, size)
                .translate(-.5f,-.5f, -.5f)

            it.applyTransformationWithInterpolation(transform)
        }
    ))

    // Render preferred orientation
    if (spider.options.debug.preferredOrientation) {
        fun model(orientation: Quaternionf, direction: Vector, thickness: Float, length: Float, material: Material) = run {
            val mTranslation = Vector3f(-1f, -1f, -1f).add(direction.toVector3f()).mul(.5f)
            val mScale = Vector3f(thickness, thickness, thickness).add(direction.toVector3f().mul(length))

            blockModel(
                location = spider.position.toLocation(spider.world),
                init = {
                    it.block = material.createBlockData()
                    it.teleportDuration = 1
                    it.interpolationDuration = 1
                },
                update = {
                    val transform = Matrix4f()
                        .rotate(orientation)
                        .scale(mScale)
                        .translate(mTranslation)

                    it.applyTransformationWithInterpolation(transform)
                }
            )
        }

        val thickness = .025f * scale
        model.add("preferredForwards", model(spider.preferredOrientation, FORWARD_VECTOR, thickness, 2.0f * scale, Material.DIAMOND_BLOCK))
        model.add("preferredRight"   , model(spider.preferredOrientation, RIGHT_VECTOR  , thickness, 1.0f * scale, Material.DIAMOND_BLOCK))
        model.add("preferredUp"      , model(spider.preferredOrientation, UP_VECTOR     , thickness, 1.0f * scale, Material.DIAMOND_BLOCK))
    }


    val normal = spider.body.normal ?: return model
    if (spider.options.debug.legPolygons && normal.contactPolygon != null) {
        val points = normal.contactPolygon//.map { it.toLocation(spider.world)}
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]

            model.add(Pair("polygon",i), lineModel(
                world = spider.world,
                position = a,
                vector = b.clone().subtract(a),
                thickness = .05f * scale,
                interpolation = 0,
                init = { it.brightness = Display.Brightness(15, 15) },
                update = { it.block = Material.EMERALD_BLOCK.createBlockData() }
            ))
        }
    }

    if (spider.options.debug.centreOfMass && normal.centreOfMass != null) model.add("centreOfMass", blockModel(
        world = spider.world,
        position = normal.centreOfMass,
        init = {
            it.teleportDuration = 1
            it.brightness = Display.Brightness(15, 15)

            val size = 0.1f * scale
            it.transformation = centredTransform(size, size, size)
        },
        update = {
            val material = if (normal.normal.horizontalLength() == .0) Material.LAPIS_BLOCK else Material.REDSTONE_BLOCK
            it.block = material.createBlockData()
        }
    ))


    if (spider.options.debug.normalForce && normal.centreOfMass != null && normal.origin !== null) model.add("acceleration", lineModel(
        world = spider.world,
        position = normal.origin,
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