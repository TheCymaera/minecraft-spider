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

        val gait = Gait.defaultWalk()
        config.getConfigurationSection("gait")?.getValues(false)?.let { KVElements.copyMap(gait, it) }

        val gallopGait = Gait.defaultGallop()
        config.getConfigurationSection("gallop_gait")?.getValues(false)?.let { KVElements.copyMap(gallopGait, it) }

        var bodyPlan =
            config.getConfigurationSection("body_plan")?.getValues(true)?.let { bodyPlanFromMap(it) } ?:
            quadrupedBodyPlan(1.0, 3).create()

        val debugOptions = DebugRendererOptions()
        config.getConfigurationSection("debug_renderer")?.getValues(false)?.let { KVElements.copyMap(debugOptions, it) }

        var target: Location? = null

        var useGallop = false

        interval(0, 1) {
            spider?.gait = if (useGallop) gallopGait else gait
            spider?.update()

            val targetVal = target ?: chainVisualizer?.target
            if (targetVal != null) targetRenderer.render(targetTemplate(targetVal))
            else targetRenderer.close()
        }

        server.scheduler.scheduleSyncRepeatingTask(this, {
            val followPlayer = firstPlayer() ?: return@scheduleSyncRepeatingTask

            target = null
            spider?.pointDetector?.player = null

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
                    spider?.let { it.behaviour = TargetBehaviour(it, followPlayer.eyeLocation, run {
                        if (it.gait.legNoStraighten) 2.0
                        else it.gait.bodyHeight * 5.0
                    }) }
                }

                Material.SHEARS -> {
                    spider?.pointDetector?.player = followPlayer
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
                            it.debugRenderer = if (it.debugRenderer == null) DebugRenderer(it, debugOptions) else null
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
                            spider = Spider(hitLocation, gait, bodyPlan)
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
                        val selectedLeg = spider?.pointDetector?.selectedLeg
                        if (selectedLeg == null) {
                            playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                            return
                        }


                        selectedLeg.isDisabled = !selectedLeg.isDisabled

                        playSound(event.player.location, Sound.BLOCK_LANTERN_PLACE, 1.0f, 1.0f)
                    }

                    Material.BREEZE_ROD -> {
                        playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        useGallop = !useGallop
                        sendActionBar(event.player, if (!useGallop) "Walk mode" else "Gallop mode")
                    }

                    else -> return
                }
            }
        }, this)

        fun parseValue(obj: Any, option: String, value: String): Any? {
            val sample = KVElements.toMap(obj)[option]
            val valueParsed = when (sample) {
                is Double -> value.toDoubleOrNull()
                is Int -> value.toIntOrNull()
                is Boolean -> value.toBoolean()
                else -> null
            }
            return valueParsed
        }

        getCommand("options")?.apply {
            val objects = mapOf(
                "gait" to { gait },
                "gallop_gait" to { gallopGait },
                "debug_renderer" to { debugOptions }
            )

            val defaultObjects = mapOf(
                "gait" to { Gait.defaultWalk().apply { scale(gait.getScale()) } },
                "gallop_gait" to { Gait.defaultGallop().apply { scale(gait.getScale()) } },
                "debug_renderer" to { DebugRendererOptions() }
            )

            setExecutor { sender, _, _, args ->
                val obj = objects[args.getOrNull(0)]?.invoke() ?: return@setExecutor false
                val default = defaultObjects[args.getOrNull(0)]?.invoke() ?: return@setExecutor false
                val option = args.getOrNull(1) ?: return@setExecutor false
                val valueUnParsed = args.getOrNull(2)

                if (option == "reset") {
                    val map = KVElements.toMap(default)
                    KVElements.copyMap(obj, map)
                    sender.sendMessage("Reset all options")
                } else if (valueUnParsed == null) {
                    val map = KVElements.toMap(obj)
                    val value = map[option]
                    sender.sendMessage("Option $option is $value")
                } else if (valueUnParsed == "reset") {
                    val value = KVElements.get(default, option)
                    KVElements.set(obj, option, value)
                    sender.sendMessage("Reset option $option to $value")
                } else {
                    val value = parseValue(obj, option, valueUnParsed)
                    if (value != null) {
                        KVElements.set(obj, option, value)
                        sender.sendMessage("Set option $option to $value")
                    } else {
                        sender.sendMessage("Invalid value for $option")
                        return@setExecutor false
                    }
                }

                instance.config.set("gait", KVElements.toMap(gait))
                instance.config.set("gallop_gait", KVElements.toMap(gallopGait))
                instance.config.set("debug_renderer", KVElements.toMap(debugOptions))
                instance.saveConfig()

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                if (args.size == 1) {
                    val options = objects.keys
                    return@setTabCompleter options.filter { it.startsWith(args.last(), true) }
                }

                if (args.size == 2) {
                    val obj = objects[args[0]]?.invoke() ?: return@setTabCompleter emptyList()
                    val options = KVElements.toMap(obj).keys + "reset"
                    return@setTabCompleter options.filter { it.startsWith(args.last(), true) }
                }

                val obj = objects[args[0]]?.invoke() ?: return@setTabCompleter emptyList()
                val map = KVElements.toMap(obj)
                val sample = map[args[1]]
                val options = (if (sample is Boolean) listOf("true", "false") else emptyList()) + "reset"
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
                "quadruped" to ::quadrupedBodyPlan,
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

                bodyPlan = builder(segmentLength, segmentCount).apply { scale(scale) }.create()

                val oldScale = gait.getScale()
                gait.scale(scale / oldScale)
                gallopGait.scale(scale / oldScale)

                instance.config.set("gait", KVElements.toMap(gait))
                instance.config.set("gallop_gait", KVElements.toMap(gallopGait))
                instance.config.set("body_plan", mapFromBodyPlan(bodyPlan))
                instance.saveConfig()

                val spiderVal = spider
                if (spiderVal != null) {
                    spiderVal.close()
                    spider = Spider(spiderVal.location, gait, bodyPlan)
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