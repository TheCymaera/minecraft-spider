package com.heledron.spideranimation

import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.spider.body.SpiderBody
import com.heledron.spideranimation.spider.misc.Cloak
import com.heledron.spideranimation.spider.misc.DirectionBehaviour
import com.heledron.spideranimation.spider.misc.PointDetector
import com.heledron.spideranimation.spider.misc.SpiderBehaviour
import com.heledron.spideranimation.spider.misc.TargetBehaviour
import com.heledron.spideranimation.spider.rendering.SpiderRenderer
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
        val spiderEntity = AppState.ecs.query<ECSEntity, SpiderBody>().firstOrNull()?.first
        if (spiderEntity == null) {
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
            spiderEntity.remove()
            player.sendActionBar("Spider removed")
        }
    }


    val disableLegComponent = CustomItemComponent("disableLeg")
    customItemRegistry += createNamedItem(Material.SHEARS, "Toggle Leg").attach(disableLegComponent)
    disableLegComponent.onHeldTick { player, _ ->
        val pointDetector = AppState.ecs.query<PointDetector>().firstOrNull()
        pointDetector?.player = player
    }
    disableLegComponent.onGestureUse { player, _ ->
        val pointDetector = AppState.ecs.query<PointDetector>().firstOrNull()
        val selectedLeg = pointDetector?.selectedLeg
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
        AppState.renderDebugVisuals = !AppState.renderDebugVisuals

        AppState.ecs.query<KinematicChainVisualizer>().forEach {
            it.detailed = AppState.renderDebugVisuals
        }

        val pitch = if (AppState.renderDebugVisuals) 2.0f else 1.5f
        playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, pitch)
    }


    val switchRendererComponent = CustomItemComponent("switchRenderer")
    customItemRegistry += createNamedItem(Material.LIGHT_BLUE_DYE, "Switch Renderer").attach(switchRendererComponent)
    switchRendererComponent.onGestureUse { player, _ ->
        AppState.ecs.query<SpiderRenderer>().forEach { renderer ->
            renderer.useParticles = !renderer.useParticles

            if (renderer.useParticles) {
                playSound(player.location, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
            } else {
                playSound(player.location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
            }
        }
    }

    val toggleCloakComponent = CustomItemComponent("toggleCloak")
    customItemRegistry += createNamedItem(Material.GREEN_DYE, "Toggle Cloak").attach(toggleCloakComponent)
    toggleCloakComponent.onGestureUse { _, _ ->
        val (cloak, entity) = AppState.ecs.query<Cloak, ECSEntity>().firstOrNull() ?: return@onGestureUse
        cloak.toggleCloak(AppState.ecs, entity)
    }

    val chainVisualizerStep = CustomItemComponent("chainVisualizerStep")
    customItemRegistry += createNamedItem(Material.PURPLE_DYE, "Chain Visualizer Step").attach(chainVisualizerStep)
    chainVisualizerStep.onGestureUse { player, _ ->
        AppState.ecs.query<KinematicChainVisualizer>().forEach {
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            it.step()
        }
    }

    val chainVisualizerStraighten = CustomItemComponent("chainVisualizerStraighten")
    customItemRegistry += createNamedItem(Material.MAGENTA_DYE, "Chain Visualizer Straighten").attach(chainVisualizerStraighten)
    chainVisualizerStraighten.onGestureUse { player, _ ->
        AppState.ecs.query<KinematicChainVisualizer>().forEach {
            playSound(player.location, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            it.straighten(it.target?.toVector() ?: return@onGestureUse)
        }
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

        val hitPosition = result?.hitPosition?.toLocation(player.world)
        AppState.target = hitPosition

        AppState.ecs.query<KinematicChainVisualizer>().forEach {
            it.target = hitPosition
            it.resetIterator()
        }

        AppState.ecs.query<ECSEntity, SpiderBody>().forEach { (spiderEntity, _) ->
            val direction = player.eyeLocation.direction.setY(0.0).normalize()

            val behaviour = if (hitPosition != null)
                TargetBehaviour(hitPosition.toVector(), 3.0) else
                DirectionBehaviour(direction, direction)

            spiderEntity.replaceComponent<SpiderBehaviour>(behaviour)
        }
    }

    val comeHereComponent = CustomItemComponent("comeHere")
    customItemRegistry += createNamedItem(Material.CARROT_ON_A_STICK, "Come Here").attach(comeHereComponent)
    comeHereComponent.onHeldTick { player, _ ->
        val (spider, entity) = AppState.ecs.query<SpiderBody, ECSEntity>().firstOrNull() ?: return@onHeldTick
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(player.eyeLocation.toVector(), run {
            val lerpedGait = spider.lerpedGait()
            if (spider.gait.straightenLegs) lerpedGait.bodyHeight * 2.0
            else lerpedGait.bodyHeight * 5.0
        }))
    }
}