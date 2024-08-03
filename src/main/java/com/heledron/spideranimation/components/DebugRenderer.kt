package com.heledron.spideranimation.components

import com.heledron.spideranimation.*
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector

class DebugRendererOptions {
    @KVElement var scanBars = true
    @KVElement var triggerZones = true
    @KVElement var endEffectors = true
    @KVElement var targetPositions = true
    @KVElement var legPolygons = true
    @KVElement var centreOfMass = true
    @KVElement var bodyAcceleration = true
    @KVElement var direction = true
}

class DebugRenderer(val spider: Spider, val options: DebugRendererOptions): SpiderComponent {
    private val renderer = MultiEntityRenderer()

    override fun close() {
        renderer.close()
    }

    override fun render() {
        renderer.beginRender()
        doRender()
        renderer.finishRender()
    }

    private fun doRender() {
        val scale = spider.gait.getScale().toFloat()

        for ((legIndex, leg) in spider.body.legs.withIndex()) {
            // Render scan bars
            if (options.scanBars) renderer.render(Pair("scanBar", legIndex), lineTemplate(
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
            if (options.triggerZones) renderer.render(Pair("triggerZoneVertical", legIndex), lineTemplate(
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
            if (options.triggerZones) renderer.render(Pair("triggerZoneHorizontal", legIndex), blockTemplate(
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
            if (options.endEffectors) renderer.render(Pair("endEffector", legIndex), blockTemplate(
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
            if (options.targetPositions) renderer.render(Pair("targetPosition", legIndex), blockTemplate(
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
        if (options.direction) renderer.render("direction", blockTemplate(
            location = spider.location.clone().add(spider.location.direction.clone().multiply(scale)),
            init = {
                it.teleportDuration = 1
                it.brightness = Display.Brightness(15, 15)

                val size = 0.1f * scale
                it.transformation = centredTransform(size, size, size)
            },
            update = {
                it.block = if (spider.isGalloping) Material.REDSTONE_BLOCK.createBlockData() else Material.EMERALD_BLOCK.createBlockData()
            }
        ))


        val normal = spider.body.normal ?: return
        if (options.legPolygons && normal.contactPolygon != null) {
            val points = normal.contactPolygon.map { it.toLocation(spider.location.world!!)}
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]

                if (options.legPolygons) renderer.render(Pair("polygon",i), lineTemplate(
                    location = a,
                    vector = b.toVector().subtract(a.toVector()),
                    thickness = .05f * scale,
                    interpolation = 0,
                    init = { it.brightness = Display.Brightness(15, 15) },
                    update = { it.block = Material.EMERALD_BLOCK.createBlockData() }
                ))
            }
        }

        if (options.centreOfMass && normal.centreOfMass != null) renderer.render("centreOfMass", blockTemplate(
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


        if (options.bodyAcceleration && normal.centreOfMass != null && normal.origin !== null) renderer.render("acceleration", lineTemplate(
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
    }


}