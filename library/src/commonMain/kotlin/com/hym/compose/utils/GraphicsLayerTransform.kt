package com.hym.compose.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin

/**
 * @author hehua2008
 * @date 2024/4/17
 */
data class GraphicsLayerTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = scaleX,
    /*@FloatRange(from = 0.0, to = 1.0)*/ val alpha: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    /*@FloatRange(from = 0.0)*/ val shadowElevation: Float = 0f,
    val ambientShadowColor: Color = DefaultShadowColor,
    val spotShadowColor: Color = DefaultShadowColor,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    /*@FloatRange(from = 0.0)*/ val cameraDistance: Float = DefaultCameraDistance,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
    val shape: Shape = RectangleShape,
    val clip: Boolean = false,
    val compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
) {
    companion object {
        val Default = GraphicsLayerTransform()
    }

    val scale: Float
        get() = if (scaleX == scaleY) scaleX else
            throw IllegalStateException("Prevent to get scale since scaleX($scaleX) != scaleY($scaleY)")

    fun toShortString(): String {
        return "{scaleX=$scaleX, scaleY=$scaleY, translationX=$translationX, translationY=$translationY, transformOrigin=(${transformOrigin.pivotFractionX}, ${transformOrigin.pivotFractionY})}"
    }

    /**
     * @see [RenderNode](https://developer.android.google.cn/reference/android/graphics/RenderNode)
     *
     *      Matrix transform = new Matrix();
     *      transform.setTranslate(renderNode.getTranslationX(), renderNode.getTranslationY());
     *      transform.preRotate(renderNode.getRotationZ(),
     *              renderNode.getPivotX(), renderNode.getPivotY());
     *      transform.preScale(renderNode.getScaleX(), renderNode.getScaleY(),
     *              renderNode.getPivotX(), renderNode.getPivotY());
     *
     */
    fun transformMatrix(width: Float, height: Float): CompatMatrix {
        val pivotX = transformOrigin.pivotFractionX * width
        val pivotY = transformOrigin.pivotFractionY * height
        val matrix = CompatMatrix()
        matrix.setTranslate(translationX, translationY)
        matrix.preRotate(rotationZ, pivotX, pivotY)
        /*
        matrix.preTranslate(pivotX, pivotY)
        matrix.preScale(scaleX, scaleY)
        matrix.preTranslate(-pivotX, -pivotY)
        */
        matrix.preScale(scaleX, scaleY, pivotX, pivotY)
        return matrix
    }
}
