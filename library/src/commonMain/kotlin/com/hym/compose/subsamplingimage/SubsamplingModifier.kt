package com.hym.compose.subsamplingimage

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.times
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateLayer
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import kotlin.math.roundToInt

/**
 * @author hehua2008
 * @date 2024/4/12
 */
/**
 * Draw the image using [subsamplingState].
 *
 * @param alignment specifies alignment of the [subsamplingState] relative to image
 * @param contentScale strategy for scaling [subsamplingState] if its size does not match the image size
 * @param alpha opacity of [subsamplingState]
 * @param colorFilter optional [ColorFilter] to apply to [subsamplingState]
 */
fun Modifier.subsampling(
    subsamplingState: SubsamplingState,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) = this then SubsamplingElement(
    subsamplingState = subsamplingState,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter
)

/**
 * Customized [ModifierNodeElement] for drawing image using [subsamplingState].
 *
 * @param subsamplingState used to draw image
 * @param alignment specifies alignment of the [subsamplingState] relative to image
 * @param contentScale strategy for scaling [subsamplingState] if its size does not match the image size
 * @param alpha opacity of [subsamplingState]
 * @param colorFilter optional [ColorFilter] to apply to [subsamplingState]
 */
private data class SubsamplingElement(
    val subsamplingState: SubsamplingState,
    val alignment: Alignment,
    val contentScale: ContentScale,
    val alpha: Float,
    val colorFilter: ColorFilter?
) : ModifierNodeElement<SubsamplingNode>() {
    override fun create(): SubsamplingNode {
        return SubsamplingNode(
            subsamplingState = subsamplingState,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )
    }

    override fun update(node: SubsamplingNode) {
        node.subsamplingState = subsamplingState
        node.alignment = alignment
        node.contentScale = contentScale
        node.alpha = alpha
        node.colorFilter = colorFilter
        node.updatePaint()

        // redraw because one of the node properties has changed.
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "SubsamplingElement"
        properties["subsamplingState"] = subsamplingState
        properties["alignment"] = alignment
        properties["contentScale"] = contentScale
        properties["alpha"] = alpha
        properties["colorFilter"] = colorFilter
    }
}

/**
 * [DrawModifier] used to draw the provided [SubsamplingState] followed by the contents
 * of the component itself
 *
 * IMPORTANT NOTE: This class sets [androidx.compose.ui.Modifier.Node.shouldAutoInvalidate]
 * to false which means it MUST invalidate both draw and the layout. It invalidates both in the
 * [SubsamplingElement.update] method through [LayoutModifierNode.invalidateLayer]
 * (invalidates draw) and [LayoutModifierNode.invalidateLayout] (invalidates layout).
 */
private class SubsamplingNode(
    var subsamplingState: SubsamplingState,
    var alignment: Alignment = Alignment.Center,
    var contentScale: ContentScale = ContentScale.Fit,
    var alpha: Float = 1f,
    var colorFilter: ColorFilter? = null
) : LayoutModifierNode, DrawModifierNode, Modifier.Node() {
    private val paint = Paint().apply {
        blendMode = DrawScope.DefaultBlendMode
        filterQuality = DrawScope.DefaultFilterQuality
        isAntiAlias = false
    }

    fun updatePaint() {
        paint.alpha = alpha
        paint.colorFilter = colorFilter
    }

    init {
        updatePaint()
    }

    override val shouldAutoInvalidate: Boolean
        get() = false

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(modifyConstraints(constraints))
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return measurable.minIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return measurable.maxIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return measurable.minIntrinsicHeight(width)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return measurable.maxIntrinsicHeight(width)
    }

    private fun modifyConstraints(constraints: Constraints): Constraints {
        val hasBoundedDimens = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val hasFixedDimens = constraints.hasFixedWidth && constraints.hasFixedHeight
        return if (hasBoundedDimens || hasFixedDimens) {
            // If we have fixed constraints or we are not attempting to size the composable based on
            // the size of the SubsamplingState, do not attempt to modify them. Otherwise rely on
            // Alignment and ContentScale to determine how to position the drawing contents of the
            // SubsamplingState within the provided bounds
            constraints.copy(minWidth = constraints.maxWidth, minHeight = constraints.maxHeight)
        } else {
            constraints
        }
    }

    override fun ContentDrawScope.draw() {
        val sourceSize = subsamplingState.sourceSize
        if (sourceSize == IntSize.Zero) {
            // Maintain the same pattern as Modifier.drawBehind to allow chaining of DrawModifiers
            drawContent()
            return
        }
        val srcSize = sourceSize.toSize()

        // Compute the offset to translate the image based on the given alignment
        // and size to draw based on the ContentScale parameter
        val scaledSize = if (size.width != 0f && size.height != 0f) {
            srcSize * contentScale.computeScaleFactor(srcSize, size)
        } else {
            Size.Zero
        }

        val alignedPosition = alignment.align(
            IntSize(scaledSize.width.roundToInt(), scaledSize.height.roundToInt()),
            IntSize(size.width.roundToInt(), size.height.roundToInt()),
            layoutDirection
        )

        val dx = alignedPosition.x.toFloat()
        val dy = alignedPosition.y.toFloat()

        // Only translate the current drawing position while delegating the SubsamplingState to draw
        // with scaled size.
        // Individual SubsamplingState implementations should be responsible for scaling their drawing
        // image accordingly to fit within the drawing area.
        translate(dx, dy) {
            subsamplingState.previewTile?.let { tile ->
                drawIntoCanvas { canvas ->
                    canvas.drawImageRect(
                        image = tile.imageBitmap ?: return@drawIntoCanvas,
                        srcSize = tile.srcSize,
                        dstOffset = tile.dstOffset,
                        dstSize = tile.dstSize,
                        paint = paint
                    )
                }
            }

            subsamplingState.displayTiles.fastForEach { tile ->
                /*
                drawImage(
                    image = tile.imageBitmap ?: return@fastForEach,
                    srcSize = tile.srcSize,
                    dstOffset = tile.dstOffset,
                    dstSize = tile.dstSize,
                    alpha = alpha,
                    style = Fill,
                    colorFilter = colorFilter,
                    blendMode = DrawScope.DefaultBlendMode,
                    filterQuality = DrawScope.DefaultFilterQuality
                )
                */
                drawIntoCanvas { canvas ->
                    canvas.drawImageRect(
                        image = tile.imageBitmap ?: return@fastForEach,
                        srcSize = tile.srcSize,
                        dstOffset = tile.dstOffset,
                        dstSize = tile.dstSize,
                        paint = paint
                    )
                }
            }
        }

        // Maintain the same pattern as Modifier.drawBehind to allow chaining of DrawModifiers
        drawContent()
    }

    override fun toString(): String =
        "SubsamplingNode(" +
                "subsamplingState=$subsamplingState, " +
                "alignment=$alignment, " +
                "contentScale=$contentScale, " +
                "alpha=$alpha, " +
                "colorFilter=$colorFilter)"
}
