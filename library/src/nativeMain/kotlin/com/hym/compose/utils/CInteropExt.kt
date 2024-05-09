package com.hym.compose.utils

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain

/**
 * @author hehua2008
 * @date 2024/5/12
 *
 * @see [KeychainSettings](https://github.com/russhwolf/multiplatform-settings/blob/main/multiplatform-settings/src/appleMain/kotlin/com/russhwolf/settings/KeychainSettings.kt)
 */
@OptIn(ExperimentalForeignApi::class)
fun <T> cfRetain(value: Any?, block: MemScope.(CFTypeRef?) -> T): T = memScoped {
    val cfValue = CFBridgingRetain(value)
    return try {
        block(cfValue)
    } finally {
        CFBridgingRelease(cfValue)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun <T> cfRetain(value1: Any?, value2: Any?, block: MemScope.(CFTypeRef?, CFTypeRef?) -> T): T =
    memScoped {
        val cfValue1 = CFBridgingRetain(value1)
        val cfValue2 = CFBridgingRetain(value2)
        return try {
            block(cfValue1, cfValue2)
        } finally {
            CFBridgingRelease(cfValue1)
            CFBridgingRelease(cfValue2)
        }
    }

@OptIn(ExperimentalForeignApi::class)
fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val keyArr = map.keys.toTypedArray()
    val valueArr = map.values.toTypedArray()
    val keyCArr = allocArrayOf(*keyArr)
    val valueCArr = allocArrayOf(*valueArr)
    val keys: CValuesRef<COpaquePointerVar> = keyCArr.reinterpret()
    val values: CValuesRef<COpaquePointerVar> = valueCArr.reinterpret()
    return CFDictionaryCreate(
        allocator = kCFAllocatorDefault,
        keys = keys,
        values = values,
        numValues = map.size.toLong(),
        keyCallBacks = null,
        valueCallBacks = null
    )
}
