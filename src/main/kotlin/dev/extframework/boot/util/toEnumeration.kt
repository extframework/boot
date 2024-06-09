package dev.extframework.boot.util

import java.util.Enumeration

public fun <T> Sequence<T>.toEnumeration() : Enumeration<T> {
    val iterator = iterator()

    return object : Enumeration<T> {
        override fun hasMoreElements(): Boolean {
            return iterator.hasNext()
        }

        override fun nextElement(): T {
            return iterator.next()
        }
    }
}