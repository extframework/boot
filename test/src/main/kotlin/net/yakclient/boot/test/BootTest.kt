package net.yakclient.boot.test

import bootFactories
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.main.initMaven
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.resolve
import net.yakclient.`object`.ObjectContainerImpl
import orThrow
import java.lang.reflect.Constructor
import java.nio.file.Files
import java.nio.file.Path

public fun testBootInstance(
    dependencies: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>>,
    location: Path = Files.createTempDirectory("boot-test"),
    dependencyTypes: DependencyTypeContainer = ObjectContainerImpl()
): BootInstance {
    class TestGraph(
        private val boot: BootInstance
    ) : SoftwareComponentGraph(
        location,
        CachingDataStore(SoftwareComponentDataAccess(location)),
        BasicArchiveResolutionProvider(
            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.ZIP_RESOLVER
        ),
        dependencyTypes,
        boot,
        dependencies.mapValuesTo(HashMap()) {
            SoftwareComponentNode(
                it.key,
                null,
                setOf(),
                setOf(),
                SoftwareComponentModel(
                    "",
                    "", null, listOf(), listOf()
                ),
                run {
                    fun <T : Any> Class<T>.tryGetConstructor(vararg params: Class<*>): Constructor<T>? =
                        net.yakclient.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }

                    fun loadFactory(cls: Class<out ComponentFactory<*, *>>): ComponentFactory<*, *> {
                        return (cls.tryGetConstructor(BootInstance::class.java)?.newInstance(boot)
                            ?: cls.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
                    }

                    loadFactory(it.value)
                }
            )
        }
    ) {
    }

    return object : BootInstance {
        override val location: Path = location
        override val dependencyTypes: DependencyTypeContainer = dependencyTypes
        override val componentGraph: SoftwareComponentGraph = TestGraph(this)

        init {
            initMaven(
                dependencyTypes,
                location resolve "m2"
            )
        }

        override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
            return componentGraph.isCached(descriptor)
        }

        override fun cache(request: SoftwareComponentArtifactRequest, location: SoftwareComponentRepositorySettings) = runBlocking {
            componentGraph.cacherOf(location).cache(request).orThrow()
        }

        override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
        ): I =
            runBlocking(bootFactories()) {
                val it = componentGraph.get(descriptor).orThrow()

                check(factoryType.isInstance(it.factory))

                ((it.factory as? ComponentFactory<T, I>)?.new(configuration)
                    ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this."))
            }
    }
}

////public fun <T : SoftwareComponent> testEnable(
////    component: T,
////    context: Map<String, String>,
////
////    base: String = System.getProperty("user.dir"),
////) {
////    read(component::class)
////
////    val boot = BootInstance.new(base)
////
////    component.onEnable(
////        ComponentContext(
////            context,
////            boot
////        )
////    )
////}
////
////public fun <T: SoftwareComponent> testDisable(
////    component: T,
////) {
////    read(component::class)
////
////    component.onDisable()
////}
//
//private fun <T: Any> read(type: KClass<T>) {
//    BootTest::class.java.module.addReads(type.java.module)
//}
//
//private inline fun <reified T> constructInternal(vararg params: List<Any>): T =
//    T::class.java.getConstructor(*params.map { it::class.java }.toTypedArray())
//        .takeIf(Constructor<T>::trySetAccessible)
//        ?.newInstance(*params)
//        ?: throw IllegalArgumentException("Failed to construct internal type: '${T::class.qualifiedName}")


