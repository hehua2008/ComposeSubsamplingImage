package com.hym.compose.utils

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @see [ShadowMatrix](https://cs.android.com/android/platform/superproject/main/+/main:external/robolectric-shadows/shadows/framework/src/main/java/org/robolectric/shadows/ShadowMatrix.java)
 *
 * The Matrix class holds a 3x3 matrix for transforming coordinates.
 *
 * @author hehua2008
 * @date 2024/4/18
 */
class CompatMatrix(src: CompatMatrix? = null) {
    private var simpleMatrix = SimpleMatrix.newIdentityMatrix()

    init {
        set(src)
    }

    val isIdentity: Boolean
        get() = simpleMatrix.equals(SimpleMatrix.IDENTITY)

    val isAffine: Boolean
        get() = simpleMatrix.isAffine

    fun rectStaysRect(): Boolean {
        return simpleMatrix.rectStaysRect()
    }

    fun getValues(values: FloatArray) {
        simpleMatrix.getValues(values)
    }

    fun setValues(values: FloatArray) {
        simpleMatrix = SimpleMatrix(values)
    }

    fun set(src: CompatMatrix?) {
        reset()
        if (src != null) {
            simpleMatrix = SimpleMatrix(getSimpleMatrix(src))
        }
    }

    fun reset() {
        simpleMatrix = SimpleMatrix.newIdentityMatrix()
    }

    fun setTranslate(dx: Float, dy: Float) {
        simpleMatrix = SimpleMatrix.translate(dx, dy)
    }

    fun setScale(sx: Float, sy: Float, px: Float, py: Float) {
        simpleMatrix = SimpleMatrix.scale(sx, sy, px, py)
    }

    fun setScale(sx: Float, sy: Float) {
        simpleMatrix = SimpleMatrix.scale(sx, sy)
    }

    fun setRotate(degrees: Float, px: Float, py: Float) {
        simpleMatrix = SimpleMatrix.rotate(degrees, px, py)
    }

    fun setRotate(degrees: Float) {
        simpleMatrix = SimpleMatrix.rotate(degrees)
    }

    fun setSinCos(sinValue: Float, cosValue: Float, px: Float, py: Float) {
        simpleMatrix = SimpleMatrix.sinCos(sinValue, cosValue, px, py)
    }

    fun setSinCos(sinValue: Float, cosValue: Float) {
        simpleMatrix = SimpleMatrix.sinCos(sinValue, cosValue)
    }

    fun setSkew(kx: Float, ky: Float, px: Float, py: Float) {
        simpleMatrix = SimpleMatrix.skew(kx, ky, px, py)
    }

    fun setSkew(kx: Float, ky: Float) {
        simpleMatrix = SimpleMatrix.skew(kx, ky)
    }

    fun setConcat(a: CompatMatrix, b: CompatMatrix): Boolean {
        simpleMatrix = getSimpleMatrix(a).multiply(getSimpleMatrix(b))
        return true
    }

    fun preTranslate(dx: Float, dy: Float): Boolean {
        return preConcat(SimpleMatrix.translate(dx, dy))
    }

    fun preScale(sx: Float, sy: Float, px: Float, py: Float): Boolean {
        return preConcat(SimpleMatrix.scale(sx, sy, px, py))
    }

    fun preScale(sx: Float, sy: Float): Boolean {
        return preConcat(SimpleMatrix.scale(sx, sy))
    }

    fun preRotate(degrees: Float, px: Float, py: Float): Boolean {
        return preConcat(SimpleMatrix.rotate(degrees, px, py))
    }

    fun preRotate(degrees: Float): Boolean {
        return preConcat(SimpleMatrix.rotate(degrees))
    }

    fun preSkew(kx: Float, ky: Float, px: Float, py: Float): Boolean {
        return preConcat(SimpleMatrix.skew(kx, ky, px, py))
    }

    fun preSkew(kx: Float, ky: Float): Boolean {
        return preConcat(SimpleMatrix.skew(kx, ky))
    }

    fun preConcat(other: CompatMatrix): Boolean {
        return preConcat(getSimpleMatrix(other))
    }

    fun postTranslate(dx: Float, dy: Float): Boolean {
        return postConcat(SimpleMatrix.translate(dx, dy))
    }

    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean {
        return postConcat(SimpleMatrix.scale(sx, sy, px, py))
    }

    fun postScale(sx: Float, sy: Float): Boolean {
        return postConcat(SimpleMatrix.scale(sx, sy))
    }

    fun postRotate(degrees: Float, px: Float, py: Float): Boolean {
        return postConcat(SimpleMatrix.rotate(degrees, px, py))
    }

    fun postRotate(degrees: Float): Boolean {
        return postConcat(SimpleMatrix.rotate(degrees))
    }

    fun postSkew(kx: Float, ky: Float, px: Float, py: Float): Boolean {
        return postConcat(SimpleMatrix.skew(kx, ky, px, py))
    }

    fun postSkew(kx: Float, ky: Float): Boolean {
        return postConcat(SimpleMatrix.skew(kx, ky))
    }

    fun postConcat(other: CompatMatrix): Boolean {
        return postConcat(getSimpleMatrix(other))
    }

    fun invert(inverse: CompatMatrix?): Boolean {
        val inverseMatrix = simpleMatrix.invert()
        if (inverseMatrix != null) {
            if (inverse != null) {
                inverse.simpleMatrix = inverseMatrix
            }
            return true
        }
        return false
    }

    fun hasPerspective(): Boolean {
        return simpleMatrix.mValues[6] != 0f || simpleMatrix.mValues[7] != 0f || simpleMatrix.mValues[8] != 1f
    }

    fun mapPoint(x: Float, y: Float): Offset {
        return simpleMatrix.transform(Offset(x, y))
    }

    fun mapPoint(point: Offset): Offset {
        return simpleMatrix.transform(point)
    }

    fun mapRect(destination: MutableRect, source: Rect): Boolean {
        val leftTop = mapPoint(source.left, source.top)
        val rightBottom = mapPoint(source.right, source.bottom)
        destination.set(
            min(leftTop.x.toDouble(), rightBottom.x.toDouble()).toFloat(),
            min(leftTop.y.toDouble(), rightBottom.y.toDouble()).toFloat(),
            max(leftTop.x.toDouble(), rightBottom.x.toDouble()).toFloat(),
            max(leftTop.y.toDouble(), rightBottom.y.toDouble()).toFloat()
        )
        return true
    }

    fun mapPoints(
        dst: FloatArray,
        dstIndex: Int,
        src: FloatArray,
        srcIndex: Int,
        pointCount: Int
    ) {
        for (i in 0 until pointCount) {
            val mapped = mapPoint(src[srcIndex + i * 2], src[srcIndex + i * 2 + 1])
            dst[dstIndex + i * 2] = mapped.x
            dst[dstIndex + i * 2 + 1] = mapped.y
        }
    }

    fun mapVectors(
        dst: FloatArray,
        dstIndex: Int,
        src: FloatArray,
        srcIndex: Int,
        vectorCount: Int
    ) {
        val transX = simpleMatrix.mValues[MTRANS_X]
        val transY = simpleMatrix.mValues[MTRANS_Y]
        simpleMatrix.mValues[MTRANS_X] = 0f
        simpleMatrix.mValues[MTRANS_Y] = 0f
        for (i in 0 until vectorCount) {
            val mapped = mapPoint(src[srcIndex + i * 2], src[srcIndex + i * 2 + 1])
            dst[dstIndex + i * 2] = mapped.x
            dst[dstIndex + i * 2 + 1] = mapped.y
        }
        simpleMatrix.mValues[MTRANS_X] = transX
        simpleMatrix.mValues[MTRANS_Y] = transY
    }

    fun mapRadius(radius: Float): Float {
        val src = floatArrayOf(radius, 0f, 0f, radius)
        mapVectors(src, 0, src, 0, 2)
        val l1 = hypot(src[0].toDouble(), src[1].toDouble()).toFloat()
        val l2 = hypot(src[2].toDouble(), src[3].toDouble()).toFloat()
        return sqrt((l1 * l2).toDouble()).toFloat()
    }

    fun setRectToRect(src: Rect, dst: Rect, stf: ScaleToFit): Boolean {
        if (src.isEmpty) {
            reset()
            return false
        }
        return simpleMatrix.setRectToRect(src, dst, stf)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is CompatMatrix && other.simpleMatrix == simpleMatrix
    }

    override fun hashCode(): Int {
        return simpleMatrix.hashCode()
    }

    private fun postConcat(matrix: SimpleMatrix): Boolean {
        simpleMatrix = matrix.multiply(simpleMatrix)
        return true
    }

    private fun preConcat(matrix: SimpleMatrix): Boolean {
        simpleMatrix = simpleMatrix.multiply(matrix)
        return true
    }

    /**
     * A simple implementation of an immutable matrix.
     */
    private class SimpleMatrix {
        val mValues: FloatArray

        internal constructor(matrix: SimpleMatrix) {
            mValues = matrix.mValues.copyOf(matrix.mValues.size)
        }

        internal constructor(values: FloatArray) {
            if (values.size != 9) {
                throw IndexOutOfBoundsException()
            }
            mValues = values.copyOf(9)
        }

        val isAffine: Boolean
            get() = mValues[6] == 0.0f && mValues[7] == 0.0f && mValues[8] == 1.0f

        fun rectStaysRect(): Boolean {
            val m00 = mValues[0]
            val m01 = mValues[1]
            val m10 = mValues[3]
            val m11 = mValues[4]
            return m00 == 0f && m11 == 0f && m01 != 0f && m10 != 0f || m00 != 0f && m11 != 0f && m01 == 0f && m10 == 0f
        }

        fun getValues(values: FloatArray) {
            if (values.size < 9) {
                throw IndexOutOfBoundsException()
            }
            mValues.copyInto(values, 0, 0, 9)
        }

        fun multiply(matrix: SimpleMatrix): SimpleMatrix {
            val values = FloatArray(9)
            for (i in values.indices) {
                val row = i / 3
                val col = i % 3
                for (j in 0..2) {
                    values[i] += mValues[row * 3 + j] * matrix.mValues[j * 3 + col]
                }
            }
            return SimpleMatrix(values)
        }

        fun invert(): SimpleMatrix? {
            val invDet = inverseDeterminant()
            if (invDet == 0f) {
                return null
            }
            val src = mValues
            val dst = FloatArray(9)
            dst[0] = cross_scale(src[4], src[8], src[5], src[7], invDet)
            dst[1] = cross_scale(src[2], src[7], src[1], src[8], invDet)
            dst[2] = cross_scale(src[1], src[5], src[2], src[4], invDet)
            dst[3] = cross_scale(src[5], src[6], src[3], src[8], invDet)
            dst[4] = cross_scale(src[0], src[8], src[2], src[6], invDet)
            dst[5] = cross_scale(src[2], src[3], src[0], src[5], invDet)
            dst[6] = cross_scale(src[3], src[7], src[4], src[6], invDet)
            dst[7] = cross_scale(src[1], src[6], src[0], src[7], invDet)
            dst[8] = cross_scale(src[0], src[4], src[1], src[3], invDet)
            return SimpleMatrix(dst)
        }

        fun transform(point: Offset): Offset {
            return Offset(
                point.x * mValues[0] + point.y * mValues[1] + mValues[2],
                point.x * mValues[3] + point.y * mValues[4] + mValues[5]
            )
        }

        // See: https://android.googlesource.com/platform/frameworks/base/+/6fca81de9b2079ec88e785f58bf49bf1f0c105e2/tools/layoutlib/bridge/src/android/graphics/Matrix_Delegate.java
        fun setRectToRect(src: Rect, dst: Rect, stf: ScaleToFit): Boolean {
            if (dst.isEmpty) {
                mValues[7] = 0f
                mValues[6] = mValues[7]
                mValues[5] = mValues[6]
                mValues[4] = mValues[5]
                mValues[3] = mValues[4]
                mValues[2] = mValues[3]
                mValues[1] = mValues[2]
                mValues[0] = mValues[1]
                mValues[8] = 1f
            } else {
                var tx = dst.width / src.width
                var sx = dst.width / src.width
                var ty = dst.height / src.height
                var sy = dst.height / src.height
                var xLarger = false
                if (stf != ScaleToFit.FILL) {
                    if (sx > sy) {
                        xLarger = true
                        sx = sy
                    } else {
                        sy = sx
                    }
                }
                tx = dst.left - src.left * sx
                ty = dst.top - src.top * sy
                if (stf == ScaleToFit.CENTER || stf == ScaleToFit.END) {
                    var diff: Float = if (xLarger) {
                        dst.width - src.width * sy
                    } else {
                        dst.height - src.height * sy
                    }
                    if (stf == ScaleToFit.CENTER) {
                        diff /= 2
                    }
                    if (xLarger) {
                        tx += diff
                    } else {
                        ty += diff
                    }
                }
                mValues[0] = sx
                mValues[4] = sy
                mValues[2] = tx
                mValues[5] = ty
                mValues[7] = 0f
                mValues[6] = mValues[7]
                mValues[3] = mValues[6]
                mValues[1] = mValues[3]
            }
            // shared cleanup
            mValues[8] = 1f
            return true
        }

        override fun equals(other: Any?): Boolean {
            return this === other || other is SimpleMatrix && equals(other as SimpleMatrix?)
        }

        fun equals(matrix: SimpleMatrix?): Boolean {
            if (matrix == null) {
                return false
            }
            for (i in mValues.indices) {
                if (!isNearlyZero(
                        matrix.mValues[i] - mValues[i]
                    )
                ) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            return mValues.contentHashCode()
        }

        private fun inverseDeterminant(): Float {
            val determinant = mValues[0] * cross(mValues[4], mValues[8], mValues[5], mValues[7]) +
                    mValues[1] * cross(mValues[5], mValues[6], mValues[3], mValues[8]) +
                    mValues[2] * cross(mValues[3], mValues[7], mValues[4], mValues[6])
            return if (isNearlyZero(determinant)) 0.0f else 1.0f / determinant
        }

        companion object {
            internal val IDENTITY = newIdentityMatrix()

            internal fun newIdentityMatrix(): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        1.0f, 0.0f, 0.0f,
                        0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun translate(dx: Float, dy: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        1.0f, 0.0f, dx,
                        0.0f, 1.0f, dy,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun scale(sx: Float, sy: Float, px: Float, py: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        sx, 0.0f, px * (1 - sx),
                        0.0f, sy, py * (1 - sy),
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun scale(sx: Float, sy: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        sx, 0.0f, 0.0f,
                        0.0f, sy, 0.0f,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun rotate(degrees: Float, px: Float, py: Float): SimpleMatrix {
                val radians = degrees.toDouble().toRadians()
                val sin = sin(radians).toFloat()
                val cos = cos(radians).toFloat()
                return sinCos(sin, cos, px, py)
            }

            fun rotate(degrees: Float): SimpleMatrix {
                val radians = degrees.toDouble().toRadians()
                val sin = sin(radians).toFloat()
                val cos = cos(radians).toFloat()
                return sinCos(sin, cos)
            }

            fun sinCos(sin: Float, cos: Float, px: Float, py: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        cos, -sin, sin * py + (1 - cos) * px,
                        sin, cos, -sin * px + (1 - cos) * py,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun sinCos(sin: Float, cos: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        cos, -sin, 0.0f,
                        sin, cos, 0.0f,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun skew(kx: Float, ky: Float, px: Float, py: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        1.0f, kx, -kx * py,
                        ky, 1.0f, -ky * px,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            fun skew(kx: Float, ky: Float): SimpleMatrix {
                return SimpleMatrix(
                    floatArrayOf(
                        1.0f, kx, 0.0f,
                        ky, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f
                    )
                )
            }

            private fun isNearlyZero(value: Float): Boolean {
                return abs(value.toDouble()) < EPSILON
            }

            private fun cross(a: Float, b: Float, c: Float, d: Float): Float {
                return a * b - c * d
            }

            private fun cross_scale(a: Float, b: Float, c: Float, d: Float, scale: Float): Float {
                return cross(a, b, c, d) * scale
            }
        }
    }

    /**
     * Controls how the src rect should align into the dst rect for setRectToRect().
     */
    enum class ScaleToFit // the native values must match those in SkMatrix.h
        (val nativeInt: Int) {
        /**
         * Scale in X and Y independently, so that src matches dst exactly. This may change the
         * aspect ratio of the src.
         */
        FILL(0),

        /**
         * Compute a scale that will maintain the original src aspect ratio, but will also ensure
         * that src fits entirely inside dst. At least one axis (X or Y) will fit exactly. START
         * aligns the result to the left and top edges of dst.
         */
        START(1),

        /**
         * Compute a scale that will maintain the original src aspect ratio, but will also ensure
         * that src fits entirely inside dst. At least one axis (X or Y) will fit exactly. The
         * result is centered inside dst.
         */
        CENTER(2),

        /**
         * Compute a scale that will maintain the original src aspect ratio, but will also ensure
         * that src fits entirely inside dst. At least one axis (X or Y) will fit exactly. END
         * aligns the result to the right and bottom edges of dst.
         */
        END(3)
    }

    companion object {
        const val TRANSLATE = "translate"
        const val SCALE = "scale"
        const val ROTATE = "rotate"
        const val SINCOS = "sincos"
        const val SKEW = "skew"
        const val MATRIX = "matrix"

        const val MSCALE_X = 0 //!< use with getValues/setValues
        const val MSKEW_X = 1 //!< use with getValues/setValues
        const val MTRANS_X = 2 //!< use with getValues/setValues
        const val MSKEW_Y = 3 //!< use with getValues/setValues
        const val MSCALE_Y = 4 //!< use with getValues/setValues
        const val MTRANS_Y = 5 //!< use with getValues/setValues
        const val MPERSP_0 = 6 //!< use with getValues/setValues
        const val MPERSP_1 = 7 //!< use with getValues/setValues
        const val MPERSP_2 = 8 //!< use with getValues/setValues

        private const val EPSILON = 1e-3f

        private fun getSimpleMatrix(matrix: CompatMatrix): SimpleMatrix {
            return matrix.simpleMatrix
        }
    }
}
