package com.heledron.spideranimation

import com.heledron.spideranimation.AppState.ecs
import com.heledron.spideranimation.kinematic_chain_visualizer.KinematicChainVisualizer
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.components.PointDetector
import com.heledron.spideranimation.spider.components.SpiderBehaviour
import com.heledron.spideranimation.spider.components.TargetBehaviour
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.target.LaserPoint
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.custom_items.CustomItemComponent
import com.heledron.spideranimation.utilities.custom_items.attach
import com.heledron.spideranimation.utilities.custom_items.createNamedItem
import com.heledron.spideranimation.utilities.custom_items.customItemRegistry
import com.heledron.spideranimation.utilities.deprecated.raycastGround
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.sendActionBar
import org.bukkit.Material
import org.bukkit.Sound
import kotlin.math.roundToInt


fun setupItems() {
    val spiderComponent = CustomItemComponent("spider")
    customItemRegistry += createNamedItem(Material.NETHERITE_INGOT, "Spider").attach(spiderComponent)
    spiderComponent.onGestureUse { player, _ ->
        val spiderEntity = AppState.ecs.query<ECSEntity, SpiderBody>().firstOrNull()?.first
        if (spiderEntity == null) {
            val yawIncrements = 45.0f
            val yaw = (player.yaw / yawIncrements).roundToInt() * yawIncrements

            val hitPosition = player.world.raycastGround(player.eyePosition, player.direction, 100.0)?.hitPosition ?: return@onGestureUse

            player.world.playSound(hitPosition, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 1.0f)
            AppState.createSpider(hitPosition.toLocation(player.world).apply { this.yaw = yaw })
            player.sendActionBar("Spider created")
        } else {
            player.world.playSound(player.position, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 0.0f)
            spiderEntity.remove()
            player.sendActionBar("Spider removed")
        }
    }


    val disableLegComponent = CustomItemComponent("disableLeg")
    customItemRegistry += createNamedItem(Material.SHEARS, "Toggle Leg").attach(disableLegComponent)
    onTick {
        val players = disableLegComponent.getPlayersHoldingItem().toSet()
        for (pointDetector in AppState.ecs.query<PointDetector>()) {
            pointDetector.checkPlayers = players
        }
    }
    disableLegComponent.onGestureUse { player, _ ->
        for (pointDetector in AppState.ecs.query<PointDetector>()) {
            val selectedLeg = pointDetector.selectedLeg[player]
            if (selectedLeg == null) {
                player.world.playSound(player.position, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
                return@onGestureUse
            }

            selectedLeg.isDisabled = !selectedLeg.isDisabled
            player.world.playSound(player.position, Sound.BLOCK_LANTERN_PLACE, 1.0f, 1.0f)
        }
    }

    val toggleDebugComponent = CustomItemComponent("toggleDebug")
    customItemRegistry += createNamedItem(Material.BLAZE_ROD, "Toggle Debug Graphics").attach(toggleDebugComponent)
    toggleDebugComponent.onGestureUse { player, _ ->
        AppState.renderDebugVisuals = !AppState.renderDebugVisuals

        AppState.ecs.query<KinematicChainVisualizer>().forEach {
            it.detailed = AppState.renderDebugVisuals
        }

        val pitch = if (AppState.renderDebugVisuals) 2.0f else 1.5f
        player.world.playSound(player.position, Sound.BLOCK_DISPENSER_FAIL, 1.0f, pitch)
    }


    val switchRendererComponent = CustomItemComponent("switchRenderer")
    customItemRegistry += createNamedItem(Material.LIGHT_BLUE_DYE, "Switch Renderer").attach(switchRendererComponent)
    switchRendererComponent.onGestureUse { player, _ ->
        AppState.ecs.query<SpiderRenderer>().forEach { renderer ->
            renderer.useParticles = !renderer.useParticles

            if (renderer.useParticles) {
                player.world.playSound(player.position, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
            } else {
                player.world.playSound(player.position, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f)
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
            player.world.playSound(player.position, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            it.step()
        }
    }

    val chainVisualizerStraighten = CustomItemComponent("chainVisualizerStraighten")
    customItemRegistry += createNamedItem(Material.MAGENTA_DYE, "Chain Visualizer Straighten").attach(chainVisualizerStraighten)
    chainVisualizerStraighten.onGestureUse { player, _ ->
        ecs.query<KinematicChainVisualizer>().forEach {
            player.world.playSound(player.position, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
            it.straighten(it.target ?: return@onGestureUse)
        }
    }

    val switchGaitComponent = CustomItemComponent("switchGait")
    customItemRegistry += createNamedItem(Material.BREEZE_ROD, "Switch Gait").attach(switchGaitComponent)
    switchGaitComponent.onGestureUse { player, _ ->
        player.world.playSound(player.position, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f)
        AppState.gallop = !AppState.gallop
        player.sendActionBar(if (!AppState.gallop) "Walk mode" else "Gallop mode")
    }

    val laserPointerComponent = CustomItemComponent("laserPointer")
    customItemRegistry += createNamedItem(Material.ARROW, "Laser Pointer").attach(laserPointerComponent)

    onTick {
        val lasers = ecs.query<ECSEntity, LaserPoint>()
        val players = laserPointerComponent.getPlayersHoldingItem()
        for (player in players) {
            val location = player.eyeLocation
            val result = raycastGround(location, location.direction, 100.0)

            val hitPosition = result?.hitPosition ?:
                player.eyePosition.add(location.direction.multiply(200))


            val isUsingFallback = result == null
            val isVisible = !isUsingFallback && AppState.miscOptions.showLaser

            val old = lasers.find { it.second.owner == player }
            if (old != null) {
                old.second.world = player.world
                old.second.position = hitPosition
                old.second.isVisible = isVisible
            } else {
                ecs.spawn(LaserPoint(
                    owner = player,
                    world = player.world,
                    position = hitPosition,
                    isVisible = isVisible,
                ))
            }
        }

        // remove unused lasers
        val unused = lasers.filter { !players.contains(it.second.owner) }
        unused.forEach { it.first.remove() }
    }

    val comeHereComponent = CustomItemComponent("comeHere")
    customItemRegistry += createNamedItem(Material.CARROT_ON_A_STICK, "Come Here").attach(comeHereComponent)
    comeHereComponent.onHeldTick { player, _ ->
        val (spider, entity) = AppState.ecs.query<SpiderBody, ECSEntity>().firstOrNull() ?: return@onHeldTick

        val distance = run {
            val lerpedGait = spider.lerpedGait()
            if (spider.gait.straightenLegs) lerpedGait.bodyHeight * 2.0
            else lerpedGait.bodyHeight * 5.0
        }

        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(player.eyePosition, distance))
    }
}