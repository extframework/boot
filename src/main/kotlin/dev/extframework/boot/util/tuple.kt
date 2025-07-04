package dev.extframework.boot.util

public typealias `2`<A, B> = Pair<A, B>

public typealias `3`<A, B, C> = Triple<A, B, C>

public typealias `4`<A, B, C, D> = Quadruple<A, B, C, D>

public data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
