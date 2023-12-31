package net.yakclient.boot.main

import bootFactories
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.zip.ZipResolutionResult
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.*
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.maven.MavenDependencyResolver
import net.yakclient.common.util.resolve
import orThrow
import java.nio.file.Path

public class ProductionBootInstance(
    override val location: Path,
    override val archiveGraph: ArchiveGraph = ArchiveGraph(location resolve "archives")
) : BootInstance {
    override val dependencyTypes: DependencyTypeContainer = DependencyTypeContainer(archiveGraph)
    override val componentResolver: SoftwareComponentResolver =
        initSoftwareComponentGraph(dependencyTypes, this)

    init {
        val maven = createMavenProvider()
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

    override suspend fun cache(
        request: SoftwareComponentArtifactRequest,
        location: SoftwareComponentRepositorySettings
    ): JobResult<Unit, ArchiveException> {
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
        return runBlocking(bootFactories() + JobName("New component: '$descriptor'")) {
            val it = archiveGraph.get(descriptor, componentResolver).orThrow()

            check(factoryType.isInstance(it.factory))

            val factory = it.factory as? ComponentFactory<T, I>
                ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this.")

            factory.new(configuration)
        }
    }
}

//public fun initMaven(
//    types: DependencyTypeContainer,
//) {
//    types.register(
//        "simple-maven",
//        createMavenProvider()
//    )
//}


private fun initSoftwareComponentGraph(
    types: DependencyTypeContainer,
    boot: BootInstance
): SoftwareComponentResolver {
    return SoftwareComponentResolver(
        BasicArchiveResolutionProvider(
            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.ZIP_RESOLVER
        ),
        types,
        boot
    )
}

public fun createMavenProvider(): DependencyResolverProvider<*, *, *> {
    val dependencyGraph = createMavenDependencyGraph()

    return object :
        DependencyResolverProvider<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
        override val name: String = "simple-maven"
        override val resolver = dependencyGraph

        override fun parseRequest(request: Map<String, String>): SimpleMavenArtifactRequest? {
            val descriptorName = request["descriptor"] ?: return null
            val isTransitive = request["isTransitive"] ?: "true"
            val scopes = request["includeScopes"] ?: "compile,runtime,import"
            val excludeArtifacts = request["excludeArtifacts"]

            return SimpleMavenArtifactRequest(
                SimpleMavenDescriptor.parseDescription(descriptorName) ?: return null,
                isTransitive.toBoolean(),
                scopes.split(',').toSet(),
                excludeArtifacts?.split(',')?.toSet() ?: setOf()
            )
        }

        override fun parseSettings(settings: Map<String, String>): SimpleMavenRepositorySettings? {
            val releasesEnabled = settings["releasesEnabled"] ?: "true"
            val snapshotsEnabled = settings["snapshotsEnabled"] ?: "true"
            val location = settings["location"] ?: return null
            val preferredHash = settings["preferredHash"] ?: "SHA1"
            val type = settings["type"] ?: "default"

            val hashType = HashType.valueOf(preferredHash)

            return when (type) {
                "default" -> SimpleMavenRepositorySettings.default(
                    location,
                    releasesEnabled.toBoolean(),
                    snapshotsEnabled.toBoolean(),
                    hashType
                )

                "local" -> SimpleMavenRepositorySettings.local(location, hashType)
                else -> return null
            }
        }
    }
}

public fun createMavenDependencyGraph(): MavenDependencyResolver {
    val resolutionProvider = object : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
        Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
        Archives.Resolvers.ZIP_RESOLVER,
    ) {}
    val graph = MavenDependencyResolver(
//        cachePath,
//        CachingDataStore(
//            MavenDataAccess(cachePath)
//        ),
        resolutionProvider
    )


    return graph
}

