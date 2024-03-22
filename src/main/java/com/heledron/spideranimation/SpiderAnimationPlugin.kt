package com.heledron.spideranimation

import com.heledron.spideranimation.chain_visualizer.KinematicChainVisualizer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.block.Action
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import kotlin.math.roundToInt

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    override fun onEnable() {
        logger.info("SpiderAnimation plugin enabled")

        val world = server.getWorld("world")!!

        val displayRenderer = DisplayRenderer(this)

        var renderer: Renderer = displayRenderer
        val debugGraphicsOptions = RenderDebugOptions.all()
        var renderDebugGraphics = false

        var spider: Spider? = null //Spider(Location(world, 0.0, 52.0, 0.0))
        val gait = Gait()
        gaitFromMap(gait, loadMapFromConfig(this, "gait"))

        var chainVisualizer: KinematicChainVisualizer? = null


        interval(this, 0, 1) {
            val valSpider = spider ?: return@interval

            valSpider.update()
            renderer.renderSpider(valSpider, if (renderDebugGraphics) debugGraphicsOptions else RenderDebugOptions.none())
        }

        server.scheduler.scheduleSyncRepeatingTask(this, {
            val followPlayer = Bukkit.getOnlinePlayers().firstOrNull() ?: return@scheduleSyncRepeatingTask

            when (followPlayer.inventory.itemInMainHand.type) {
                Material.ARROW -> {
                    val location = followPlayer.eyeLocation
                    val result = location.world.rayTraceBlocks(location, location.direction, 100.0, FluidCollisionMode.NEVER, true)

                    if (result == null) {
                        val direction = followPlayer.eyeLocation.direction.setY(0.0).normalize()
                        spider?.behaviour = DirectionBehaviour(direction)

                        chainVisualizer?.apply {
                            this.target = null
                            this.resetIterator()
                            this.render(world, false)
                        }
                    } else {
                        val target = result.hitPosition.toLocation(followPlayer.world)
                        spider?.behaviour = TargetBehaviour(target, 1.0, true)

                        chainVisualizer?.apply {
                            this.target = target.toVector()
                            this.resetIterator()
                            this.render(world, false)
                        }
                    }
                }

                Material.CARROT_ON_A_STICK -> {
                    spider?.behaviour = TargetBehaviour(followPlayer.location, 5.0, false)
                }
                else -> {
                    spider?.behaviour = StayStillBehaviour
                }
            }

        }, 0, 1)

        // /summon minecraft:area_effect_cloud -26 -11 26 {Tags:["spider.chain_visualizer"]}
        // on spawn: create a chain visualizer
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onEntitySpawn(event: org.bukkit.event.entity.EntitySpawnEvent) {
                if (!event.entity.scoreboardTags.contains("spider.chain_visualizer")) return

                val location = event.entity.location

                val chainVisVal = chainVisualizer
                if (chainVisVal != null) {
                    chainVisVal.unRender()
                    chainVisualizer = null
                } else {
                    chainVisualizer = KinematicChainVisualizer.create(3, 1.5, location.toVector(), displayRenderer).apply {
                        render(world, true)
                    }
                }

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
                        event.player.location.world.playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, if (renderDebugGraphics) 1.5f else 2.0f)
                        renderDebugGraphics = !renderDebugGraphics
                    }
                    Material.LIGHT_BLUE_DYE -> {
                        spider?.apply { renderer.clearSpider(this) }
                        renderer = if (renderer == displayRenderer) {
                            event.player.location.world.playSound(event.player.location, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
                            ParticleRenderer
                        } else {
                            event.player.location.world.playSound(event.player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
                            displayRenderer
                        }
                    }
                    Material.NETHERITE_INGOT -> {
                        val spiderVal = spider
                        if (spiderVal == null) {
                            val yawIncrements = 45.0f
                            val yaw = event.player.location.yaw// + 180.0f;
                            val yawRounded = (yaw / yawIncrements).roundToInt() * yawIncrements

                            val location = event.player.rayTraceBlocks(100.0, FluidCollisionMode.NEVER)?.hitPosition?.toLocation(world) ?: return

                            location.yaw = yawRounded
                            location.world.playSound(location, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 1.0f)
                            spider = Spider(location, gait)
                            event.player.sendActionBar(Component.text("Spider created"))
                        } else {
                            event.player.location.world.playSound(event.player.location, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.0f)
                            renderer.clearSpider(spiderVal)
                            spider = null
                            event.player.sendActionBar(Component.text("Spider removed"))
                        }
                    }


                    Material.PURPLE_DYE -> {
                        val chainVisVal = chainVisualizer ?: return
                        event.player.location.world.playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        chainVisVal.step()
                        chainVisVal.render(world, false)
                    }
                    Material.MAGENTA_DYE -> {
                        val chainVisVal = chainVisualizer ?: return
                        val target = chainVisVal.target ?: return
                        event.player.location.world.playSound(event.player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                        chainVisVal.straighten(target)
                        chainVisVal.render(world, true)
                    }
                    else -> return
                }
            }
        }, this)

        // default: /setgait .2
        // big step: /setgait .2 .2
        // eldritch horror: /setgait 1 1.6

//        getCommand("setgait")?.apply {
//            setExecutor { sender, _, _, args ->
//                val spiderVal = spider ?: return@setExecutor false
//
//                val walkSpeed = args.getOrNull(0)?.toDoubleOrNull()
//                val legSpeed = args.getOrNull(1)?.toDoubleOrNull()
//
//
//                if (walkSpeed == null) {
//                    return@setExecutor false
//                }
//
//                spiderVal.gait = if (legSpeed != null) Gait(walkSpeed, legSpeed) else Gait(walkSpeed)
//
//                sender.sendMessage(
//                    "Set gait to:\n" +
//                            String.format("walk_speed: %.2f\n", spiderVal.gait.walkSpeed) +
//                            String.format("leg_speed: %.2f\n", spiderVal.gait.legSpeed)
//                )
//                return@setExecutor true
//            }
//        }

        getCommand("stage")?.apply {
            setExecutor { sender, _, _, args ->
                val stage = args.getOrNull(0)?.toIntOrNull() ?: return@setExecutor false

                val name = setStage(gait, debugGraphicsOptions, stage)

                if (name != null) sender.sendMessage("Set stage $stage: $name")
                else sender.sendMessage("Unknown stage: $stage")

                saveMapToConfig(this@SpiderAnimationPlugin, "gait", gaitToMap(gait))

                return@setExecutor true
            }
        }

        getCommand("gait")?.apply {
            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0) ?: return@setExecutor false


                if (option == "reset") {
                    gaitFromMap(gait, gaitToMap(Gait()))
                    sender.sendMessage("Reset gait options")
                } else {
                    val valueUnParsed = args.getOrNull(1) ?: return@setExecutor false

                    val map = gaitToMap(gait)
                    if (valueUnParsed == "reset") {
                        val defaultOption = gaitToMap(Gait())[option]
                        if (defaultOption != null) map[option] = defaultOption
                    } else {
                        val sample = map[option]
                        val value = when (sample) {
                            is Double -> valueUnParsed.toDoubleOrNull()
                            is Int -> valueUnParsed.toIntOrNull()
                            is Boolean -> valueUnParsed.toBoolean()
                            else -> null
                        }
                        if (value != null) map[option] = value
                    }
                    gaitFromMap(gait, map)
                    sender.sendMessage("Set gait option $option to ${map[option]}")
                }

                saveMapToConfig(this@SpiderAnimationPlugin, "gait", gaitToMap(gait))

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                val default = gaitToMap(Gait())

                val options = if (args.size == 1) {
                    listOf("reset", *default.keys.toTypedArray())
                } else {
                    val sample = default[args[0]]
                    if (sample is Boolean) listOf("true", "false","reset")
                    else listOf("reset")
                }

                return@setTabCompleter options.filter { it.startsWith(args.last(), true) }
            }
        }

        getCommand("debug")?.apply {
            setExecutor { sender, _, _, args ->
                val option = args.getOrNull(0)
                val value = args.getOrNull(1)?.toBoolean()

                when (option) {
                    "legTarget" -> debugGraphicsOptions.legTarget = value ?: !debugGraphicsOptions.legTarget
                    "legTriggerZone" -> debugGraphicsOptions.legTriggerZone = value ?: !debugGraphicsOptions.legTriggerZone
                    "legRestPosition" -> debugGraphicsOptions.legRestPosition = value ?: !debugGraphicsOptions.legRestPosition
                    "legEndEffector" -> debugGraphicsOptions.legEndEffector = value ?: !debugGraphicsOptions.legEndEffector
                    "spiderDirection" -> debugGraphicsOptions.spiderDirection = value ?: !debugGraphicsOptions.spiderDirection
                    else -> sender.sendMessage("Unknown option: $option")
                }

                return@setExecutor true
            }

            setTabCompleter { _, _, _, args ->
                val options = if (args.size == 1) {
                    listOf("legTarget", "legTriggerZone", "legRestPosition", "legEndEffector", "spiderDirection")
                } else {
                    listOf("true", "false")
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
    }

    override fun onDisable() {
        DisplayRegistry.clearAll()
    }
}

fun runLater(plugin: Plugin, delay: Long, task: () -> Unit): Closeable {
    val handler = plugin.server.scheduler.runTaskLater(plugin, task, delay)
    return Closeable {
        handler.cancel()
    }
}

fun interval(plugin: Plugin, delay: Long, period: Long, task: () -> Unit): Closeable {
    val handler = plugin.server.scheduler.runTaskTimer(plugin, task, delay, period)
    return Closeable {
        handler.cancel()
    }
}

fun sendDebugMessage(message: String) {
    val player = Bukkit.getOnlinePlayers().firstOrNull() ?: return
    player.sendActionBar(message)
}