package net.yakclient.boot.main

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.maven.MavenResolverProvider
import net.yakclient.common.util.resolve
import runBootBlocking
import java.nio.file.Path

public class ProductionBootInstance(
    override val location: Path,
    override val archiveGraph: ArchiveGraph = ArchiveGraph(location resolve "archives")
) : BootInstance {
    override val dependencyTypes: DependencyTypeContainer = DependencyTypeContainer(archiveGraph)
    override val componentResolver: SoftwareComponentResolver =
        initSoftwareComponentGraph(dependencyTypes, this)

    init {
        val maven = MavenResolverProvider()
        dependencyTypes.register(
            "simple-maven",
            maven
        )
        archiveGraph.registerResolver(componentResolver)
        println()
    }

    override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
        return archiveGraph[descriptor] is SoftwareComponentNode
    }

    override fun cache(
        request: SoftwareComponentArtifactRequest,
        location: SoftwareComponentRepositorySettings
    ): Job<Unit> {
        return archiveGraph.cache(
            request,
            location,
            componentResolver
        )
    }

    public override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
        descriptor: SoftwareComponentDescriptor,
        factoryType: Class<out ComponentFactory<T, I>>,
        configuration: T
    ): I {
        return runBootBlocking(JobName("New component: '$descriptor'")) {
            val it = archiveGraph.get(descriptor, componentResolver)().merge()

            check(factoryType.isInstance(it.factory))

            val factory = it.factory as? ComponentFactory<T, I>
                ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this.")

            factory.new(configuration)
        }
    }
}

private fun initSoftwareComponentGraph(
    types: DependencyTypeContainer,
    boot: BootInstance
): SoftwareComponentResolver {
    return SoftwareComponentResolver(
        types,
        boot,
        BootInstance::class.java.classLoader
    )
}