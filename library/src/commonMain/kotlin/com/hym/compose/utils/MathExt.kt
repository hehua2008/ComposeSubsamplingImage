package com.hym.compose.utils

/**
 * @author hehua2008
 * @date 2024/4/18
 */
/**
 * Constant by which to multiply an angular value in degrees to obtain an
 * angular value in radians.
 */
const val DEGREES_TO_RADIANS = 0.017453292519943295

/**
 * Constant by which to multiply an angular value in radians to obtain an
 * angular value in degrees.
 */
const val RADIANS_TO_DEGREES = 57.29577951308232

/**
 * Converts an angle measured in degrees to an approximately
 * equivalent angle measured in radians.  The conversion from
 * degrees to radians is generally inexact.
 *
 * @return  the measurement of the angle `angdeg`
 * in radians.
 */
fun Double.toRadians(): Double {
    return this * DEGREES_TO_RADIANS
}

/**
 * Converts an angle measured in radians to an approximately
 * equivalent angle measured in degrees.  The conversion from
 * radians to degrees is generally inexact; users should
 * *not* expect `cos(toRadians(90.0))` to exactly
 * equal `0.0`.
 *
 * @return  the measurement of the angle `angrad`
 * in degrees.
 */
fun Double.toDegrees(): Double {
    return this * RADIANS_TO_DEGREES
}
