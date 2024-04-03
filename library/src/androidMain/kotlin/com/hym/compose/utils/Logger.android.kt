package com.hym.compose.utils

import android.util.Log

/**
 * @author hehua2008
 * @date 2024/4/5
 */
actual object Logger {
    actual fun v(tag: String, msg: String, e: Throwable?) {
        Log.v(tag, msg, e)
    }

    actual fun d(tag: String, msg: String, e: Throwable?) {
        Log.d(tag, msg, e)
    }

    actual fun i(tag: String, msg: String, e: Throwable?) {
        Log.i(tag, msg, e)
    }

    actual fun w(tag: String, msg: String, e: Throwable?) {
        Log.w(tag, msg, e)
    }

    actual fun e(tag: String, msg: String, e: Throwable?) {
        Log.e(tag, msg, e)
    }
}
