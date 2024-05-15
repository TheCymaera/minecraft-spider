package com.heledron.spideranimation

import com.heledron.spideranimation.components.*
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.event.block.Action
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import kotlin.math.roundToInt

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: SpiderAnimationPlugin
    }

    val closables = mutableListOf<Closeable>()

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        closables.forEach { it.close() }
    }

    override fun onEnable() {
        var spider: Spider? = null
        var chainVisualizer: KinematicChainVisualizer? = null
        val targetRenderer = EntityRenderer<BlockDisplay>()

        closables += Closeable {
            spider?.close()
            chainVisualizer?.close()
            targetRenderer.close()
        }

        logger.info("Enabling Spider Animation plugin")

        instance = this

        var gait = Gait.defaultWalk()
        config.getConfigurationSection("gait")?.getValues(false)?.let { gaitFromMap(gait, it) }

        var gallopGait = Gait.defaultGallop()
        config.getConfigurationSection("gallop_gait")?.getValues(false)?.let { gaitFromMap(gallopGait, it) }


        var bodyPlanBuilder =
            config.getConfigurationSection("body_plan")?.getValues(true)?.let { bodyPlanFromMap(it) } ?:
            quadripedBodyPlan(1.0, 3)

        var target: Location? = null

        interval(0, 1) {
            spider?.update()

            val targetVal = target ?: chainVisualizer?.target
            if (targetVal != null) targetRenderer.render(targetTemplate(targetVal))
            else targetRenderer.close()
        }

        fun getEndEffectorInLineOfSight(location: Location): Leg? {
            val spiderVal = spider ?: return null
            if (spiderVal.location.world != location.world) return null

            val locationAsVector = location.toVector()
            val direction = location.direction
            for (leg in spiderVal.body.legs) {
                val lookingAt = lookingAtPoint(locationAsVector, direction, leg.endEffector, spiderVal.gait.bodyHeight * .15)
                if (lookingAt) return leg
            }
            return null
        }

        server.scheduler.scheduleSyncRepeatingTask(this, {
            val followPlayer = firstPlayer() ?: return@scheduleSyncRepeatingTask

            target = null

            spider?.body?.legs?.forEach { leg -> leg.isSelected = false }

            when (followPlayer.inventory.itemInMainHand.type) {
                Material.ARROW -> {
                    val location = followPlayer.eyeLocation
                    val result = raycastGround(location, location.direction, 100.0)

                    if (result == null) {
                        val direction = followPlayer.eyeLocation.direction.setY(0.0).normalize()
                        spider?.let { it.behaviour = DirectionBehaviour(it, direction, direction) }

                        chainVisualizer?.let {
                            it.target = null
                            it.resetIterator()
                        }
                    } else {
                        val targetVal = result.hitPosition.toLocation(followPlayer.world)
                        target = targetVal

                        chainVisualizer?.let {
                            it.target = targetVal
                            it.resetIterator()
                        }

                        spider?.let { it.behaviour = TargetBehaviour(it, targetVal, it.gait.bodyHeight) }
                    }
                }

                Material.CARROT_ON_A_STICK -> {
                    spider?.let { it.behaviour = TargetBehaviour(it, followPlayer.eyeLocation, it.gait.bodyHeight * 5.0) }
                }

                Material.SHEARS -> {
                    val selectedLeg = getEndEffectorInLineOfSight(followPlayer.eyeLocation)
                    selectedLeg?.let { it.isSelected = true }
                }

                else -> {
                    spider?.let { it.behaviour = StayStillBehaviour(it) }
                }
            }
        }, 0, 1)

        // /summon minecraft:area_effect_cloud -26 -11 26 {Tags:["spider.chain_visualizer"]}
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
                if (!event.entity.scoreboardTags.contains("spider.chain_visualizer")) return

                val location = event.entity.location

                chainVisualizer?.close()
                chainVisualizer = if (chainVisualizer != null) null else KinematicChainVisualizer.create(3, 1.5, location)

                event.entity.remove()
            }
        }, this)


        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
                if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

                if (event.action == Action.RIGHT_CLICK_BLOCK && !(event.clickedBlock?.type?.isInteractable == false || event.player.isSneaking)) return

                when (event.item?.type) {
                    Material.BLAZE_ROD -> {
                        val pitch = if (spider?.debugRenderer == null) 2.0f else 1.5f
                        playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, pitch)

                        spider?.let {
                            it.debugRenderer = if (it.debugRenderer == null) DebugRenderer(it) else null
                        }

                        chainVisualizer?.detailed = !chainVisualizer!!.detailed
                    }

                    Material.LIGHT_BLUE_DYE -> {
                        val spiderVal = spider ?: return
                        spiderVal.renderer = if (spiderVal.renderer is SpiderEntityRenderer) {
                            playSound(event.player.location, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
                            ParticleRenderer(spiderVal)
                        } else {
                            playSound(event.player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
                            SpiderEntityRenderer(spiderVal)
                        }
                    }

                    Material.GREEN_DYE -> {
                        spider?.cloak?.toggleCloak()
                    }

                    Material.NETHERITE_INGOT -> {
                        val spiderVal = spider
                        if (spiderVal == null) {
                            val yawIncrements = 45.0f
                            val yaw = event.player.location.yaw// + 180.0f;
                            val yawRounded = (yaw / yawIncrements).roundToInt() * yawIncrements

                            val playerLocation = event.player.eyeLocation
                            val hitLocation = raycastGround(playerLocation, playerLocation.direction, 100.0)?.hitPosition?.toLocation(playerLocation.world!!) ?: return

                            hitLocation.yaw = yawRounded
                            playSound(hitLocation, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 1.0f)
                            spider = Spider(hitLocation, gait, bodyPlanBuilder.create())
                            sendActionBar(event.player, "Spider created")
                        } else {
                            playSound(event.player.location, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.0f)
                            spider?.close()
                            spider = null
                            sendActionBar(event.player, "Spider removed")
                        }
                    }


                    Material.PURPLE_DYE -> {
                        val chainVisVal = chainVisualizer ?: return
                        playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        chainVisVal.step()
                    }

                    Material.MAGENTA_DYE -> {
                        val chainVisVal = chainVisualizer ?: return
                        playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        chainVisVal.straighten(chainVisVal.target?.toVector() ?: return)
                    }

                    Material.SHEARS -> {
                        val selectedLeg = getEndEffectorInLineOfSight(event.player.eyeLocation)
                        if (selectedLeg == null) {
                            playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                            return
                        }


                        selectedLeg.isDisabled = !selectedLeg.isDisabled

                        playSound(event.player.location, Sound.BLOCK_LANTERN_PLACE, 1.0f, 1.0f)
                    }

                    Material.NETHER_STAR -> {
                        playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        val spiderVal = spider ?: return
                        spiderVal.gait = if (spiderVal.gait == gait) {
                            sendActionBar(event.player, "Gallop mode")
                            gallopGait
                        } else {
                            sendActionBar(event.player, "Walk mode")
                            gait
                        }
                    }

                    else -> return
                }
            }
        }, this)

        getCommand("gait")?.apply {
            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false

                if (option == "reset") {
                    gait = Gait.defaultWalk().apply { scale(bodyPlanBuilder.scale) }
                    gallopGait = Gait.defaultGallop().apply { scale(bodyPlanBuilder.scale) }
                    spider?.gait = gait
                    sender.sendMessage("Reset gait options")
                } else {
                    val valueUnParsed = args.getOrNull(1)

                    if (valueUnParsed == null) {
                        val map = gaitToMap(spider?.gait ?: gait)
                        val value = map[option]
                        sender.sendMessage("Gait option $option is $value")
                    } else {
                        val sample = gaitToMap(gait)[option]
                        val value = when (sample) {
                            is Double -> valueUnParsed.toDoubleOrNull()
                            is Int -> valueUnParsed.toIntOrNull()
                            is Boolean -> valueUnParsed.toBoolean()
                            else -> null
                        }
                        if (value != null) {
                            setGaitValue(spider?.gait ?: gait, option, value)
                            sender.sendMessage("Set gait option $option to $value")
                        } else {
                            sender.sendMessage("Invalid value for $option")
                            return@setExecutor false
                        }
                    }
                }

                instance.config.set("gait", gaitToMap(gait))
                instance.config.set("gallop_gait", gaitToMap(gallopGait))
                instance.saveConfig()

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                val map = gaitToMap(gait)

                val options = if (args.size == 1) {
                    listOf("reset", *map.keys.toTypedArray())
                } else {
                    val sample = map[args[0]]
                    if (sample is Boolean) listOf("true", "false")
                    else listOf()
                }

                return@setTabCompleter options.filter { it.startsWith(args.last(), true) }
            }
        }

        getCommand("fall")?.setExecutor { sender, _, _, args ->
            val spiderVal = spider ?: return@setExecutor true

            val height = args[0].toDoubleOrNull()

            if (height == null) {
                sender.sendMessage("Usage: /spider:fall <height>")
                return@setExecutor true
            }

            spiderVal.teleport(spiderVal.location.clone().add(0.0, height, 0.0))


            return@setExecutor true
        }

        getCommand("body_plan")?.apply {
            val bodyPlanTypes = mapOf(
                "quadriped" to ::quadripedBodyPlan,
                "hexapod" to ::hexapodBodyPlan,
                "octopod" to ::octopodBodyPlan
            )

            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false

                val scale = args.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                val segmentLength = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0
                val segmentCount = args.getOrNull(3)?.toIntOrNull() ?: 3

                val builder = bodyPlanTypes[option]
                if (builder == null) {
                    sender.sendMessage("Invalid body plan: $option")
                    return@setExecutor true
                }

                val oldScale = bodyPlanBuilder.scale

                bodyPlanBuilder = builder(segmentLength, segmentCount).apply { scale(scale) }
                instance.config.set("body_plan", mapFromBodyPlan(bodyPlanBuilder))

                gait.scale(scale / oldScale)
                gallopGait.scale(scale / oldScale)
                instance.config.set("gait", gaitToMap(gait))
                instance.config.set("gallop_gait", gaitToMap(gallopGait))

                instance.saveConfig()

                val spiderVal = spider
                if (spiderVal != null) {
                    spiderVal.close()
                    spider = Spider(spiderVal.location, gait, bodyPlanBuilder.create())
                }

                sender.sendMessage("Set body plan to $option")

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                if (args.size == 1) {
                    val options = bodyPlanTypes.keys
                    return@setTabCompleter options.filter { it.startsWith(args.last(), true) }
                }
                return@setTabCompleter emptyList()
            }
        }
    }
}