package net.yakclient.boot.mixin

public annotation class InjectSource(
    val to: String,
    val placement: Placement,
    val priority: Int = 0
)

public annotation class InjectMethod(
    val priority: Int = 0
)

public annotation class InjectField(
    val priority: Int = 0
)