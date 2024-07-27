package dev.extframework.boot.util

public inline fun <reified T> typeOf() : Class<T> = T::class.java