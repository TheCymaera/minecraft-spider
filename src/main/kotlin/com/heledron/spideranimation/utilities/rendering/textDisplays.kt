package com.heledron.spideranimation.utilities.rendering

import com.heledron.spideranimation.utilities.currentPlugin
import com.heledron.spideranimation.utilities.eyePosition
import com.heledron.spideranimation.utilities.maths.normal
import com.heledron.spideranimation.utilities.maths.shear
import com.heledron.spideranimation.utilities.maths.toRadians
import com.heledron.spideranimation.utilities.position
import org.bukkit.Bukkit
import org.bukkit.entity.TextDisplay
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.sign

val textDisplayUnitSquare: Matrix4f; get() = Matrix4f()
    .translate(-0.1f + .5f,-0.5f + .5f,0f)
    .scale(8.0f,4.0f,1f) //  + 0.003f  + 0.001f

// Left aligned
val textDisplayUnitTriangle; get() = listOf(
    // Left
    Matrix4f().scale(.5f).mul(textDisplayUnitSquare),
    // Right
    Matrix4f().scale(.5f).translate(1f, 0f, 0f).shear(yx = -1f).mul(textDisplayUnitSquare),
    // Top
    Matrix4f().scale(.5f).translate(0f,1f,0f).shear(xy = -1f).mul(textDisplayUnitSquare),
)

// Right aligned
//private val textDisplayUnitTriangleMutable = listOf(
//    // Right
//    Matrix4f().scale(.5f).shear(1f, 0f, 0f).mul(textDisplayUnitSquare),
//    // Left
//    Matrix4f().scale(.5f).translate(1f, 0f, 0f).mul(textDisplayUnitSquare),
//    // Top
//    Matrix4f().scale(.5f).translate(1f,0f,0f).shear(0f, 1f, 0f).mul(textDisplayUnitSquare),
//)

class TextDisplayTriangleResult(
    val transforms: List<Matrix4f>,
    val xAxis: Vector3f,
    val yAxis: Vector3f,
    val zAxis: Vector3f,
    val height: Float,
    val width: Float,
    val rotation: Quaternionf,
    val shear: Float,
)

fun textDisplayTriangle(
    point1: Vector3f,
    point2: Vector3f,
    point3: Vector3f,
): TextDisplayTriangleResult {
    val p2 = Vector3f(point2).sub(point1)
    val p3 = Vector3f(point3).sub(point1)

    val zAxis = Vector3f(p2).cross(p3).normalize()
    val xAxis = Vector3f(p2).normalize()
    val yAxis = Vector3f(zAxis).cross(xAxis).normalize()

    val width = p2.length()
    val height = Vector3f(p3).dot(yAxis)
    val p3Width = Vector3f(p3).dot(xAxis)

    val rotation = Quaternionf().lookAlong(Vector3f(zAxis).mul(-1f), yAxis).conjugate()

    val shear = p3Width / width
    // val shear = - (1f - p3Width / width)

    val transform = Matrix4f()
        .translate(point1)
        .rotate(rotation)
        .scale(width, height, 1f)
        .shear(yx = shear)

    return TextDisplayTriangleResult(
        transforms = textDisplayUnitTriangle.map { unit -> Matrix4f(transform).mul(unit) },
        xAxis = xAxis,
        yAxis = yAxis,
        zAxis = zAxis,
        height = height,
        width = width,
        rotation = rotation,
        shear = shear,
    )
}



fun TextDisplay.cull() {
    val visiblePosition = position.toVector3f().add(transformation.translation)
    val normal = transformation.normal()

    Bukkit.getOnlinePlayers().forEach { player ->
        val direction = player.eyePosition.toVector3f().sub(visiblePosition)
        val angle = direction.angle(normal)

        val isFacing = angle < 95.0f.toRadians()
        if (isFacing) {
            player.showEntity(currentPlugin, this)
        } else {
            player.hideEntity(currentPlugin, this)
        }
    }
}

fun TextDisplay.interpolateTriangleTransform(matrix: Matrix4f) {
    val oldTransformation = this.transformation
    setTransformationMatrix(matrix)

    val rightRotationChange = Quaternionf(oldTransformation.rightRotation).difference(transformation.rightRotation)
        .getEulerAnglesXYZ(Vector3f())

    if (abs(rightRotationChange.z) >= 45f.toRadians()) {
        this.transformation = this.transformation.apply {
            val rot = (-90f).toRadians() * rightRotationChange.z.sign

            leftRotation.rotateZ(-rot)
            scale.set(scale.y, scale.x, scale.z)
            rightRotation.set(Quaternionf().rotateZ(rot).mul(rightRotation))
        }
    }

    if (oldTransformation == this.transformation) return
    this.interpolationDelay = 0
}