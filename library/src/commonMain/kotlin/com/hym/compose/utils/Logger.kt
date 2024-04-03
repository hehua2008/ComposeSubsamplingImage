package com.hym.compose.utils

/**
 * @author hehua2008
 * @date 2024/4/5
 */
expect object Logger {
    fun v(tag: String, msg: String, e: Throwable? = null)

    fun d(tag: String, msg: String, e: Throwable? = null)

    fun i(tag: String, msg: String, e: Throwable? = null)

    fun w(tag: String, msg: String, e: Throwable? = null)

    fun e(tag: String, msg: String, e: Throwable? = null)
}
