package com.heledron.spideranimation.utilities.inverse_kinematics

import com.heledron.spideranimation.utilities.LineSegment
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.rendering.EmptyRenderItem
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import com.heledron.spideranimation.utilities.rendering.RenderItem
import com.heledron.spideranimation.utilities.rendering.renderLine
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class IKDebugStyle(
    val hingeArcRadiusFraction: Double = 0.6,
    val swingConeLengthFraction: Double = 1.0,
    val twistArcDistanceFraction: Double = 0.8,
    val twistArcRadius: Double = 0.3,
    val twistIndicatorLength: Double = 0.4,
    val axisLength: Double = 0.6,
    val targetAxisLength: Double = 1.0,

    val hingeArcSteps: Int = 18,
    val twistArcSteps: Int = 10,
    val coneRingSteps: Int = 12,
    val coneSpokes: Int = 3,

    val drawNodeAxes: Boolean = true,
    val drawConstraints: Boolean = true,

    val hingeColor: Material = Material.LIGHT_BLUE_CONCRETE,
    val swingColor: Material = Material.CYAN_CONCRETE,
    val twistArcColor: Material = Material.PINK_CONCRETE,
    val twistIndicatorColor: Material = Material.MAGENTA_CONCRETE,
) {
    fun hingeArcRadius(length: Double) = length * hingeArcRadiusFraction

    fun swingConeLength(length: Double) = length * swingConeLengthFraction
}

fun renderIKGuides(
    world: World,
    chain: IKChain3D,
    pose: IKChainPose3D,
    target: IKTarget3D? = null,
): RenderGroup {
	val style = IKDebugStyle()
	val out = RenderGroup()
    val nodes = chain.getWorldNodes(pose)

    if (style.drawConstraints) {
        out["constraints"] = renderConstraints(world, chain, pose, nodes, style)
    }

    if (style.drawNodeAxes) {
        for (index in nodes.indices) {
            val node = nodes[index]
            out["axes" to index] = renderAxes(
                world = world,
                position = node.position,
                rotation = node.worldRotation,
                length = style.axisLength,
                thickness = 0.015f,
            )
        }
    }

    val targetOrientation = target?.orientation
    if (targetOrientation != null) out["target"] = renderAxes(
        world = world,
        position = target.position,
        rotation = targetOrientation,
        length = style.targetAxisLength,
		thickness = 0.015f,
    )
    
    return out
}

private fun renderConstraints(
    world: World,
    chain: IKChain3D,
    pose: IKChainPose3D,
    nodes: List<IKChainWorldNode3D>,
    style: IKDebugStyle,
): RenderGroup {
	val out = RenderGroup()
    for (index in chain.segments.indices) {
        val segment = chain.segments[index]
        val parent = nodes[index]
        val child = nodes[index + 1]

        // Use the next segment's length if length is 0.
        val guideLength = segment.length.takeIf { it > 1e-9 }
            ?: chain.segments.getOrNull(index + 1)?.length
            ?: 0.0
        if (guideLength <= 1e-9) continue

        when (val joint = segment.joint) {
            is IKHingeJoint3D -> out[index] = addHingeGuide(
                world = world,
                length = guideLength,
                parent = parent,
                joint = joint,
                style = style,
            )

            is IKSwingTwistJoint3D -> out[index] = addSwingTwistGuide(
                world = world,
                length = guideLength,
                parent = parent,
                child = child,
                joint = joint,
                style = style,
            )
        }
    }
    return out
}

private fun addHingeGuide(
    world: World,
    length: Double,
    parent: IKChainWorldNode3D,
    joint: IKHingeJoint3D,
    style: IKDebugStyle,
): RenderGroup {
    val arc = arc(
        origin = parent.position,
        rotation = parent.worldRotation,
        axis = joint.axis,
        forward = joint.reference,
        radius = style.hingeArcRadius(length),
        minAngle = joint.minAngle,
        maxAngle = joint.maxAngle,
        steps = style.hingeArcSteps,
    )

    return renderArc(world, arc, style.hingeColor, .02f)
}

private fun addSwingTwistGuide(
    world: World,
    length: Double,
    parent: IKChainWorldNode3D,
    child: IKChainWorldNode3D,
    joint: IKSwingTwistJoint3D,
    style: IKDebugStyle,
): RenderGroup {
    val axisWorld = IKChain3D.CHAIN_AXIS.rotate(child.worldRotation)
    val axisInParent = axisWorld.clone().rotate(Quaternionf(parent.worldRotation).invert())

    val swingRotation = quaternionFromTo(IKChain3D.CHAIN_AXIS, axisInParent, Vector(1, 0, 0))
    val swingBasis = Quaternionf(parent.worldRotation).mul(swingRotation)

    val coneLength = style.swingConeLength(length)
    val sinSwing = sin(joint.maxSwing)
    val cosSwing = cos(joint.maxSwing)
    
    val thickness = .02f
    val twistIndicatorThickness = .0175f

    val rim = (0 until style.coneRingSteps).map { step ->
        val angle = 2 * PI * step / style.coneRingSteps
        val offset = Vector3f(
            (cos(angle) * sinSwing).toFloat(),
            (sin(angle) * sinSwing).toFloat(),
            cosSwing.toFloat(),
        ).mul(coneLength.toFloat())

        localToWorld(parent.position, parent.worldRotation, offset)
    }
    
    val out = RenderGroup()

    out["swingRing"] = renderRing(world, rim, style.swingColor, thickness)

    for (spoke in 0 until style.coneSpokes) {
        val tip = rim[spoke * style.coneRingSteps / style.coneSpokes]
        out["swingSpoke$spoke"] = renderLine(world, parent.position, tip, style.swingColor, thickness)
    }

    val ringCenterOffset = Vector3f(0f, 0f, (length * style.twistArcDistanceFraction).toFloat())

    val twistArc = arc(
        origin = parent.position,
        rotation = swingBasis,
        axis = IKChain3D.CHAIN_AXIS,
        forward = joint.referenceAxis,
        radius = style.twistArcRadius,
        minAngle = joint.minTwist,
        maxAngle = joint.maxTwist,
        steps = style.twistArcSteps,
        centerOffset = ringCenterOffset,
    )

    out["twistArc"] = renderArc(world, twistArc, style.twistArcColor, thickness)

    val indicatorRotation = Quaternionf(child.worldRotation)
        .mul(quaternionFromTo(IKChain3D.CHAIN_AXIS, joint.referenceAxis, Vector(1, 0, 0)))

    val ringCenterWorld = parent.position.clone()
        .add(axisWorld.clone().multiply(length * style.twistArcDistanceFraction))
    val indicatorTip = ringCenterWorld.clone()
        .add(
            IKChain3D.CHAIN_AXIS.rotate(indicatorRotation)
                .multiply(style.twistIndicatorLength)
        )

    out["twistIndicator"] = renderLine(world, ringCenterWorld, indicatorTip, style.twistIndicatorColor, twistIndicatorThickness)
    return out
}

private fun orthogonalize(vector: Vector3f, axis: Vector3f): Vector3f {
    val out = Vector3f(vector)
    out.sub(Vector3f(axis).mul(vector.dot(axis)))

    if (out.lengthSquared() < 1e-10f) {
        // `vector` was parallel to `axis`; fall back to any perpendicular axis
        out.set(0f, 1f, 0f)
        out.sub(Vector3f(axis).mul(out.dot(axis)))

        if (out.lengthSquared() < 1e-10f) {
            out.set(1f, 0f, 0f)
        }
    }

    return out.normalize()
}

private fun renderLine(
    world: World,
    start: Vector,
    end: Vector,
    material: Material,
    thickness: Float,
): RenderItem {
    if (start.distanceSquared(end) <= 1.0e-12) return EmptyRenderItem

    return renderLine(
        world = world,
        line = LineSegment(start, end),
        thickness = thickness,
        interpolation = 1,
        init = {
            it.brightness = Display.Brightness(15, 15)
        },
        update = {
            it.block = material.createBlockData()
        },
    )
}

private fun renderArc(
    world: World,
    arc: WorldArc,
    material: Material,
    thickness: Float,
): RenderGroup {
	val out = RenderGroup()
    for (index in 0 until arc.points.size - 1) {
        out[index] = renderLine(world, arc.points[index], arc.points[index + 1], material, thickness)
    }

    out["spokeStart"] = renderLine(world, arc.pivot, arc.points.first(), material, thickness)
    out["spokeEnd"] = renderLine(world, arc.pivot, arc.points.last(), material, thickness)

    return out
}

private fun renderRing(
    world: World,
    points: List<Vector>,
    material: Material,
    thickness: Float,
): RenderGroup {
	val out = RenderGroup()
    for (index in points.indices) {
        val next = points[(index + 1) % points.size]
        out[index] = renderLine(world, points[index], next, material, thickness)
    }
    return out
}

private fun renderAxes(
    world: World,
    position: Vector,
    rotation: Quaternionf,
    length: Double,
    thickness: Float,
): RenderGroup {
    val axes = listOf(
        Vector(1.0, 0.0, 0.0) to Material.RED_CONCRETE,
        Vector(0.0, 1.0, 0.0) to Material.LIME_CONCRETE,
        Vector(0.0, 0.0, 1.0) to Material.BLUE_CONCRETE,
    )

	val out = RenderGroup()
    for ((index, axisAndBlock) in axes.withIndex()) {
        val (axis, block) = axisAndBlock
        val tip = position.clone().add(axis.clone().rotate(rotation).multiply(length))
        out[index] = renderLine(world, position, tip, block, thickness)
    }
    return out
}





private class WorldArc(
    val pivot: Vector,
    val points: List<Vector>,
)

private fun arc(
    origin: Vector,
    rotation: Quaternionf,
    axis: Vector,
    forward: Vector,
    radius: Double,
    minAngle: Double,
    maxAngle: Double,
    steps: Int,
    centerOffset: Vector3f = Vector3f(),
): WorldArc {
    val axis3f = axis.toVector3f().normalize()
    val forward3f = orthogonalize(forward.toVector3f(), axis3f)
    val side3f = Vector3f(axis3f).cross(forward3f).normalize()

    val points = (0..steps).map { step ->
        val angle = (minAngle + (maxAngle - minAngle) * step / steps).toFloat()
        val cosine = cos(angle)
        val sine = sin(angle)
        val scale = radius.toFloat()

        val point = Vector3f(forward3f).mul(cosine * scale)
            .add(Vector3f(side3f).mul(sine * scale))
            .add(centerOffset)

        localToWorld(origin, rotation, point)
    }

    return WorldArc(
        pivot = localToWorld(origin, rotation, centerOffset),
        points = points,
    )
}

private fun localToWorld(origin: Vector, rotation: Quaternionf, local: Vector3f): Vector {
    val rotated = Vector3f(local).rotate(rotation)
    return Vector(
        origin.x + rotated.x,
        origin.y + rotated.y,
        origin.z + rotated.z,
    )
}