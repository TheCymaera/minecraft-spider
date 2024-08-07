package com.heledron.spideranimation.items

import com.heledron.spideranimation.*
import com.heledron.spideranimation.spider.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt

fun registerItems() {
    CustomItemRegistry.initialize()

    CustomItemRegistry.items += CustomItem(
        id = "spider",
        defaultItem = createNamedItem(Material.NETHERITE_INGOT, "Spider"),
        onRightClick = { player ->
            val spider = AppState.spider
            if (spider == null) {
                val yawIncrements = 45.0f
                val yaw = player.location.yaw// + 180.0f;
                val yawRounded = (yaw / yawIncrements).roundToInt() * yawIncrements

                val playerLocation = player.eyeLocation
                val hitLocation = raycastGround(playerLocation, playerLocation.direction, 100.0)?.hitPosition?.toLocation(playerLocation.world!!) ?: return@CustomItem

                hitLocation.yaw = yawRounded
                playSound(hitLocation, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 1.0f)
                AppState.spider = AppState.createSpider(hitLocation)
                sendActionBar(player, "Spider created")
            } else {
                playSound(player.location, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.0f)
                AppState.spider = null
                sendActionBar(player, "Spider removed")
            }
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "disableLeg",
        defaultItem = createNamedItem(Material.SHEARS, "Toggle Leg"),
        onHeldTick = { player ->
            AppState.spider?.pointDetector?.player = player
        },
        onRightClick = { player ->
            val selectedLeg = AppState.spider?.pointDetector?.selectedLeg
            if (selectedLeg == null) {
                playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                return@CustomItem
            }

            selectedLeg.isDisabled = !selectedLeg.isDisabled
            playSound(player.location, Sound.BLOCK_LANTERN_PLACE, 1.0f, 1.0f)
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "toggleDebug",
        defaultItem = createNamedItem(Material.BLAZE_ROD, "Toggle Debug Graphics"),
        onRightClick = { player ->
            val pitch = if (AppState.spider?.debugRenderer == null) 2.0f else 1.5f
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, pitch)

            AppState.spider?.let {
                it.debugRenderer = if (it.debugRenderer == null) DebugRenderer(it) else null
            }

            AppState.chainVisualizer?.detailed = !AppState.chainVisualizer!!.detailed
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "switchRenderer",
        defaultItem = createNamedItem(Material.LIGHT_BLUE_DYE, "Switch Renderer"),
        onRightClick = { player ->
            val spider = AppState.spider ?: return@CustomItem

            spider.renderer = if (spider.renderer is SpiderEntityRenderer) {
                playSound(player.location, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
                ParticleRenderer(spider)
            } else {
                playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
                SpiderEntityRenderer(spider)
            }
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "toggleCloak",
        defaultItem = createNamedItem(Material.GREEN_DYE, "Toggle Cloak"),
        onRightClick = {
            AppState.spider?.cloak?.toggleCloak()
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "chainVisStep",
        defaultItem = createNamedItem(Material.PURPLE_DYE, "Step"),
        onRightClick = { player ->
            val chainVisVal = AppState.chainVisualizer ?: return@CustomItem
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            chainVisVal.step()
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "chainVisStraighten",
        defaultItem = createNamedItem(Material.MAGENTA_DYE, "Straighten"),
        onRightClick = { player ->
            val chainVisVal = AppState.chainVisualizer ?: return@CustomItem
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            chainVisVal.straighten(chainVisVal.target?.toVector() ?: return@CustomItem)
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "switchGait",
        defaultItem = createNamedItem(Material.BREEZE_ROD, "Switch Gait"),
        onRightClick = { player ->
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            AppState.spiderOptions.gallop = !AppState.spiderOptions.gallop
            sendActionBar(player, if (!AppState.spiderOptions.gallop) "Walk mode" else "Gallop mode")
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "laserPointer",
        defaultItem = createNamedItem(Material.ARROW, "Laser Pointer"),
        onHeldTick = { player ->
            val location = player.eyeLocation
            val result = raycastGround(location, location.direction, 100.0)

            if (result == null) {
                val direction = player.eyeLocation.direction.setY(0.0).normalize()
                AppState.spider?.let { it.behaviour = DirectionBehaviour(it, direction, direction) }

                AppState.chainVisualizer?.let {
                    it.target = null
                    it.resetIterator()
                }
            } else {
                val targetVal = result.hitPosition.toLocation(player.world)
                AppState.target = targetVal

                AppState.chainVisualizer?.let {
                    it.target = targetVal
                    it.resetIterator()
                }

                AppState.spider?.let { it.behaviour = TargetBehaviour(it, targetVal, it.gait.bodyHeight) }
            }
        }
    )

    CustomItemRegistry.items += CustomItem(
        id = "comeHere",
        defaultItem = ItemStack(Material.CARROT_ON_A_STICK),
        onHeldTick = { player ->
            AppState.spider?.let { it.behaviour = TargetBehaviour(it, player.eyeLocation, run {
                if (it.gait.legNoStraighten) 2.0
                else it.gait.bodyHeight * 5.0
            }) }
        }
    )
}