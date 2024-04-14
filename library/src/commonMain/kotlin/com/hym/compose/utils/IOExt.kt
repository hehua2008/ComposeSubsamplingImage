package com.hym.compose.utils

import okio.BufferedSource
import okio.Source

/**
 * @author hehua2008
 * @date 2024/4/12
 */
fun <T> SourceMarker.reusableRead(block: (BufferedSource) -> T): T {
    return try {
        mark(Long.MAX_VALUE)
        block(source())
    } finally {
        reset(0L)
    }
}

/** Closes this, ignoring any checked exceptions. */
fun Source.closeQuietly() {
    try {
        close()
    } catch (rethrown: RuntimeException) {
        throw rethrown
    } catch (_: Exception) {
    }
}
