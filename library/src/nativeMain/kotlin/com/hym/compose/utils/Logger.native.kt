package com.hym.compose.utils

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * @author hehua2008
 * @date 2024/4/5
 */
actual object Logger {
    init {
        Napier.base(DebugAntilog())
    }

    actual fun v(tag: String, msg: String, e: Throwable?) {
        Napier.v(message = msg, throwable = e, tag = tag)
    }

    actual fun d(tag: String, msg: String, e: Throwable?) {
        Napier.d(message = msg, throwable = e, tag = tag)
    }

    actual fun i(tag: String, msg: String, e: Throwable?) {
        Napier.i(message = msg, throwable = e, tag = tag)
    }

    actual fun w(tag: String, msg: String, e: Throwable?) {
        Napier.w(message = msg, throwable = e, tag = tag)
    }

    actual fun e(tag: String, msg: String, e: Throwable?) {
        Napier.e(message = msg, throwable = e, tag = tag)
    }
}
