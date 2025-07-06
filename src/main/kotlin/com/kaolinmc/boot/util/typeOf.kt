package com.kaolinmc.boot.util

public inline fun <reified T> typeOf() : Class<T> = T::class.java