package com.hym.compose.utils

import androidx.compose.runtime.Stable

/**
 * @author hehua2008
 * @date 2024/4/16
 */
private val EmptyList = emptyList<Nothing>()

private object EmptyEqualityList : EqualityList<Nothing>(EmptyList), List<Nothing> by EmptyList

fun <E> emptyEqualityList(): EqualityList<E> {
    return EmptyEqualityList
}

private val EmptyImmutableEqualityList = ImmutableEqualityList(EmptyList)

@Stable
fun <E> emptyImmutableEqualityList(): ImmutableEqualityList<E> {
    return EmptyImmutableEqualityList as ImmutableEqualityList<E>
}

@Stable
fun <E> equalityListOf(vararg e: E): ImmutableEqualityList<E> {
    return ImmutableEqualityList(listOf(*e))
}

fun <E> mutableEqualityListOf(): MutableEqualityList<E> {
    return MutableEqualityList(mutableListOf())
}

sealed class EqualityList<out E>(protected val delegate: List<E>) : List<E> {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualityList<*>) return false
        if (size != other.size) return false
        val iterator = iterator()
        val otherIterator = other.iterator()
        while (true) {
            val hasNext = iterator.hasNext()
            val otherHasNext = otherIterator.hasNext()
            if (hasNext != otherHasNext) {
                return false // The sizes are not equal now, this or other list has changed
            } else if (hasNext) {
                val next = iterator.next()
                val otherNext = otherIterator.next()
                if (next != otherNext) return false
            } else { // hasNext == otherHasNext == false
                return true
            }
        }
    }

    final override fun hashCode(): Int {
        var result = 1
        for (element in delegate) {
            result = 31 * result + (element?.hashCode() ?: 0)
        }
        return result
    }

    final override fun toString(): String {
        return delegate.joinToString(prefix = "[", postfix = "]")
    }
}

@Stable
class ImmutableEqualityList<E>(delegate: List<E>) : EqualityList<E>(delegate),
    List<E> by delegate

class MutableEqualityList<E>(delegate: MutableList<E>) : EqualityList<E>(delegate),
    MutableList<E> by delegate {

    @Stable
    fun toImmutableEqualityList(): ImmutableEqualityList<E> {
        return ImmutableEqualityList(delegate.toList())
    }
}
