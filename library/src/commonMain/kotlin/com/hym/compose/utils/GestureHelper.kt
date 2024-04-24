package com.hym.compose.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * @author hehua2008
 * @date 2024/4/24
 */
suspend fun PointerInputScope.detectTapWithoutConsume(onTap: (Offset) -> Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(pass = PointerEventPass.Initial)
        //firstDown.consume()
        val firstUp = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            ?: return@awaitEachGesture
        //firstUp.consume()

        // check for second tap
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
            var change: PointerInputChange
            // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
            do {
                change = awaitFirstDown(pass = PointerEventPass.Initial)
            } while (change.uptimeMillis < minUptime)
            change
        }

        if (secondDown == null) {
            // no valid second tap started
            onTap(firstUp.position)
        } else {
            // Second tap down detected
            val secondUp = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            if (secondUp != null) {
                //secondUp.consume()
            } else {
                onTap(firstUp.position)
            }
        }
    }
}
