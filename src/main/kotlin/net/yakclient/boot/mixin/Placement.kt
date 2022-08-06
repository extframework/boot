package net.yakclient.boot.mixin

import kotlin.reflect.KClass

public annotation class Placement(
    val custom : KClass<out CustomPlacement>
) {

}

public interface CustomPlacement {

}