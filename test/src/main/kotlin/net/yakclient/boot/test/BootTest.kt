package net.yakclient.boot.test

import net.yakclient.boot.BootContext
import net.yakclient.boot.MavenPopulateContext
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.component.SoftwareComponent
import net.yakclient.boot.createMavenProvider
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

private class BootTest private constructor()

public fun <T : SoftwareComponent> testEnable(
    component: T,
    context: Map<String, String>,

    mavenCache: String = System.getProperty("user.dir"),
    initMaven: (MavenPopulateContext.(String) -> Boolean) -> Unit = {}
) {
    read(component::class)

    val bootContext: BootContext = constructInternal()

    bootContext.dependencyProviders.add(
        createMavenProvider(mavenCache, initMaven)
    )

    component.onEnable(
        ComponentContext(
            context,
            bootContext
        )
    )
}

public fun <T: SoftwareComponent> testDisable(
    component: T,
) {
    read(component::class)

    component.onDisable()
}

private fun <T: Any> read(type: KClass<T>) {
    BootTest::class.java.module.addReads(type.java.module)
}

private inline fun <reified T> constructInternal(vararg params: List<Any>): T =
    T::class.java.getConstructor(*params.map { it::class.java }.toTypedArray())
        .takeIf(Constructor<T>::trySetAccessible)
        ?.newInstance(*params)
        ?: throw IllegalArgumentException("Failed to construct internal type: '${T::class.qualifiedName}")


