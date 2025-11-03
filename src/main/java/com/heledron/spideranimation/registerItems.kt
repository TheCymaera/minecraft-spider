package com.heledron.spideranimation

import com.heledron.spideranimation.spider.misc.DirectionBehaviour
import com.heledron.spideranimation.spider.rendering.SpiderParticleRenderer
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
import com.heledron.spideranimation.spider.misc.TargetBehaviour
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.custom_items.CustomItemComponent
import com.heledron.spideranimation.utilities.custom_items.attach
import com.heledron.spideranimation.utilities.custom_items.createNamedItem
import com.heledron.spideranimation.utilities.custom_items.customItemRegistry
import com.heledron.spideranimation.utilities.deprecated.raycastGround
import com.heledron.spideranimation.utilities.sendActionBar
import org.bukkit.Material
import org.bukkit.Sound
import kotlin.math.roundToInt


fun registerItems() {
    val spiderComponent = CustomItemComponent("spider")
    customItemRegistry += createNamedItem(Material.NETHERITE_INGOT, "Spider").attach(spiderComponent)
    spiderComponent.onGestureUse { player, _ ->
        val spider = AppState.spider
        if (spider == null) {
            val yawIncrements = 45.0f
            val yaw = player.location.yaw// + 180.0f;
            val yawRounded = (yaw / yawIncrements).roundToInt() * yawIncrements

            val playerLocation = player.eyeLocation
            val hitLocation = raycastGround(playerLocation, playerLocation.direction, 100.0)?.hitPosition?.toLocation(playerLocation.world!!) ?: return@onGestureUse

            hitLocation.yaw = yawRounded
            playSound(hitLocation, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 1.0f)
            AppState.createSpider(hitLocation)
            player.sendActionBar("Spider created")
        } else {
            playSound(player.location, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.0f)
            AppState.spider = null
            player.sendActionBar("Spider removed")
        }
    }


    val disableLegComponent = CustomItemComponent("disableLeg")
    customItemRegistry += createNamedItem(Material.SHEARS, "Toggle Leg").attach(disableLegComponent)
    disableLegComponent.onHeldTick { player, _ ->
        AppState.spider?.pointDetector?.player = player
    }
    disableLegComponent.onGestureUse { player, _ ->
        val selectedLeg = AppState.spider?.pointDetector?.selectedLeg
        if (selectedLeg == null) {
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            return@onGestureUse
        }

        selectedLeg.isDisabled = !selectedLeg.isDisabled
        playSound(player.location, Sound.BLOCK_LANTERN_PLACE, 1.0f, 1.0f)
    }

    val toggleDebugComponent = CustomItemComponent("toggleDebug")
    customItemRegistry += createNamedItem(Material.BLAZE_ROD, "Toggle Debug Graphics").attach(toggleDebugComponent)
    toggleDebugComponent.onGestureUse { player, _ ->
        AppState.showDebugVisuals = !AppState.showDebugVisuals
        AppState.chainVisualizer?.detailed = AppState.showDebugVisuals
        val pitch = if (AppState.showDebugVisuals) 2.0f else 1.5f
        playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, pitch)
    }


    val switchRendererComponent = CustomItemComponent("switchRenderer")
    customItemRegistry += createNamedItem(Material.LIGHT_BLUE_DYE, "Switch Renderer").attach(switchRendererComponent)
    switchRendererComponent.onGestureUse { player, _ ->
        val spider = AppState.spider ?: return@onGestureUse

        spider.renderer = if (spider.renderer is SpiderRenderer) {
            playSound(player.location, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
            SpiderParticleRenderer(spider)
        } else {
            playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
            SpiderRenderer(spider)
        }
    }

    val toggleCloakComponent = CustomItemComponent("toggleCloak")
    customItemRegistry += createNamedItem(Material.GREEN_DYE, "Toggle Cloak").attach(toggleCloakComponent)
    toggleCloakComponent.onGestureUse { _, _ ->
        AppState.spider?.cloak?.toggleCloak()
    }

    val chainVisualizerStep = CustomItemComponent("chainVisualizerStep")
    customItemRegistry += createNamedItem(Material.PURPLE_DYE, "Chain Visualizer Step").attach(chainVisualizerStep)
    chainVisualizerStep.onGestureUse { player, _ ->
        val chainVisVal = AppState.chainVisualizer ?: return@onGestureUse
        playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
        chainVisVal.step()
    }

    val chainVisualizerStraighten = CustomItemComponent("chainVisualizerStraighten")
    customItemRegistry += createNamedItem(Material.MAGENTA_DYE, "Chain Visualizer Straighten").attach(chainVisualizerStraighten)
    chainVisualizerStraighten.onGestureUse { player, _ ->
        val chainVisVal = AppState.chainVisualizer ?: return@onGestureUse
        playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
        chainVisVal.straighten(chainVisVal.target?.toVector() ?: return@onGestureUse)
    }

    val switchGaitComponent = CustomItemComponent("switchGait")
    customItemRegistry += createNamedItem(Material.BREEZE_ROD, "Switch Gait").attach(switchGaitComponent)
    switchGaitComponent.onGestureUse { player, _ ->
        playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
        AppState.gallop = !AppState.gallop
        player.sendActionBar(if (!AppState.gallop) "Walk mode" else "Gallop mode")
    }

    val laserPointerComponent = CustomItemComponent("laserPointer")
    customItemRegistry += createNamedItem(Material.ARROW, "Laser Pointer").attach(laserPointerComponent)

    laserPointerComponent.onHeldTick { player, _ ->
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

            AppState.spider?.let { it.behaviour = TargetBehaviour(it, targetVal.toVector(), it.lerpedGait.bodyHeight) }
        }
    }

    val comeHereComponent = CustomItemComponent("comeHere")
    customItemRegistry += createNamedItem(Material.CARROT_ON_A_STICK, "Come Here").attach(comeHereComponent)
    comeHereComponent.onHeldTick { player, _ ->
        AppState.spider?.let {
            it.behaviour = TargetBehaviour(it, player.eyeLocation.toVector(), run {
                if (it.gait.straightenLegs) it.lerpedGait.bodyHeight * 2.0
                else it.lerpedGait.bodyHeight * 5.0
            })
        }
    }
}