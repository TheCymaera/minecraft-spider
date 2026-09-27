package com.heledron.spideranimation.inverse_kinematics

import com.heledron.spideranimation.spider.presets.SpiderLegModel
import com.heledron.spideranimation.utilities.DisplayModel
import com.heledron.spideranimation.utilities.centredTransform
import com.heledron.spideranimation.utilities.ecs.Component
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.inverse_kinematics.*
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import com.heledron.spideranimation.utilities.rendering.interpolateTransform
import com.heledron.spideranimation.utilities.rendering.renderBlock
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val random = Random.Default

private const val TICK_SECONDS = 1.0 / 20.0
private const val TARGET_LERP_PER_SECOND = 1.5

private const val RETARGET_INTERVAL_MIN_TICKS = 44
private const val RETARGET_INTERVAL_MAX_TICKS = 84

class IKDemo(
    val world: World,
    val chain: IKChain3D,
    val pose: IKChainPose3D,
    val solver: IKSolver3D,
    val evaluator: IKEvaluator3D,
    val models: List<DisplayModel>,
) : Component {
    private val targetOrigin = pose.position.clone()
        .add(IKChain3D.CHAIN_AXIS.rotate(pose.orientation).multiply(0.7))

    private var desiredTarget = randomTarget()

    val target: IKTarget3D = desiredTarget.clone()

    private var retargetTimer = randomRetargetTicks()

    var evaluation: IKEvaluation3D = evaluator(chain, pose, target); private set

    var renderDebugVisuals = false

    fun update() {
        retargetTimer -= 1
        if (retargetTimer <= 0) {
            desiredTarget = randomTarget()
            retargetTimer = randomRetargetTicks()
        }

        // Exponential smoothing, framerate independent
        val targetLerp = 1 - exp(-TICK_SECONDS * TARGET_LERP_PER_SECOND)
        target.lerp(desiredTarget, targetLerp)

        val solvedPose = pose.clone()
        solver(chain, solvedPose, target)

        pose.copy(solvedPose)

        evaluation = evaluator(chain, pose, target)
    }

    fun render(): RenderGroup {
        val group = RenderGroup()
        val nodes = chain.getWorldNodes(pose)

        for (index in chain.segments.indices) {
            val start = nodes[index]
            val end = nodes[index + 1]

            val transform = Matrix4f()
                .rotate(end.worldRotation)

            for (piece in models[index].pieces) {
                group[index to piece] = renderBlock(
                    world = world,
                    position = start.position,
                    init = {
                        it.teleportDuration = 1
                        it.interpolationDuration = 1
                        it.brightness = Display.Brightness(0, 15)
                    },
                    update = {
                        it.interpolateTransform(Matrix4f(transform).mul(piece.transform))
                        it.block = piece.block
                        it.brightness = piece.brightness
                    }
                )
            }
        }

        group["target"] = renderBlock(
            world = world,
            position = target.position,
            init = {
                it.teleportDuration = 1
                it.brightness = Display.Brightness(15, 15)
                it.transformation = centredTransform(.3f, .3f, .3f)
            },
            update = {
                it.block = if (evaluation.isSolved) {
                    Material.LIME_CONCRETE.createBlockData()
                } else {
                    Material.RED_CONCRETE.createBlockData()
                }
            }
        )

        group["endEffector"] = renderBlock(
            world = world,
            position = nodes.last().position,
            init = {
                it.teleportDuration = 1
                it.brightness = Display.Brightness(15, 15)
                it.transformation = centredTransform(.16f, .16f, .16f)
            },
            update = {
                it.block = Material.DIAMOND_BLOCK.createBlockData()
            }
        )

        if (renderDebugVisuals) {
            group.renderIKGuides(
                world = world,
                chain = chain,
                pose = pose,
                target = target,
                handlePrefix = "guide",
            )
        }

        return group
    }

    private fun randomRetargetTicks() =
        random.nextInt(RETARGET_INTERVAL_MIN_TICKS, RETARGET_INTERVAL_MAX_TICKS)

    private fun randomVectorInRadius(min: Double, max: Double): Vector {
        val theta = random.nextDouble(0.0, PI * 2)
        val vertical = random.nextDouble(-1.0, 1.0)
        val horizontal = sqrt(1 - vertical * vertical)
        val length = cbrt(random.nextDouble()) * (max - min) + min

        return Vector(
            horizontal * cos(theta),
            vertical,
            horizontal * sin(theta),
        ).multiply(length)
    }

    private fun randomTarget(): IKTarget3D {
        val totalReach = chain.totalLength
        val position = randomVectorInRadius(totalReach * 0.2, totalReach * 0.8).add(targetOrigin)
        return IKTarget3D(position)
    }

    companion object {
        fun create(world: World, location: Location): IKDemo {
            val chain = createDemoChain()
            val evaluator = createIKEvaluator3D(tolerance = 0.01)

            return IKDemo(
                world = world,
                chain = chain,
                pose = chain.createPose(location.toVector(), Quaternionf().rotateX((-PI / 2).toFloat())),
                solver = createDampedLeastSquaresIKSolver3D(evaluator),
                evaluator = evaluator,
                models = legModelsFor(chain.segments),
            )
        }
    }
}

private fun createDemoChain() = IKChain3D(
    listOf(
        IKChainSegment3D(
            length = 0.80,
            joint = IKSwingTwistJoint3D(
                maxSwing = 0.20 * PI,
                minTwist = -PI,
                maxTwist = PI,
                referenceAxis = Vector(0, 1, 0),
            ),
        ),
        IKChainSegment3D(
            length = 1.05,
            joint = IKHingeJoint3D(
                minAngle = -0.30 * PI,
                maxAngle = 0.30 * PI,
                axis = Vector(1, 0, 0),
                reference = Vector(0, 0, 1),
            ),
        ),
        IKChainSegment3D(
            length = 0.95,
            joint = IKHingeJoint3D(
                minAngle = -0.30 * PI,
                maxAngle = 0.30 * PI,
                axis = Vector(1, 0, 0),
                reference = Vector(0, 0, 1),
            ),
        ),
        IKChainSegment3D(
            length = 0.80,
            joint = IKSwingTwistJoint3D(
                maxSwing = 0.22 * PI,
                minTwist = -0.4 * PI,
                maxTwist = 0.4 * PI,
                referenceAxis = Vector(0, 1, 0),
            ),
        ),
        IKChainSegment3D(
            length = 0.65,
            joint = IKSwingTwistJoint3D(
                maxSwing = 0.25 * PI,
                minTwist = -0.4 * PI,
                maxTwist = 0.4 * PI,
                referenceAxis = Vector(0, 1, 0),
            ),
        ),
    )
)

private fun legModelsFor(segments: List<IKChainSegment3D>): List<DisplayModel> {
    return segments.mapIndexed { index, segment ->
        val model = when (index) {
            0 -> SpiderLegModel.BASE
            segments.size - 2 -> SpiderLegModel.TIBIA
            segments.size - 1 -> SpiderLegModel.TIP
            else -> SpiderLegModel.FEMUR
        }

        model.clone().scale(1f, 1f, segment.length.toFloat())
    }
}

fun setupIKDemo(app: ECS) {
    app.onTick {
        for (demo in app.query<IKDemo>()) {
            demo.update()
        }
    }

    app.onRender {
        for (demo in app.query<IKDemo>()) {
            demo.render().submit(demo)
        }
    }
}
