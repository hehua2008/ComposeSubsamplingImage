package com.hym.compose.subsamplingimage

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.Data
import org.jetbrains.skia.impl.Managed
import org.jetbrains.skia.impl.NativePointer
import platform.darwin.UInt8Var

/**
 * @author hehua2008
 * @date 2024/5/11
 */
@OptIn(ExperimentalForeignApi::class)
class ManagedCFBytes private constructor(
    val bytes: CPointer<UInt8Var>,
    val size: Int,
    val ptr: NativePointer
) : Managed(ptr, NullPointer, false) {
    constructor(bytes: CPointer<UInt8Var>, size: Int) : this(
        bytes,
        size,
        bytes.rawValue as NativePointer
    )

    fun toData(): Data {
        return Data.makeWithoutCopy(ptr, size, this)
    }
}
