package net.yakclient.boot.main

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.zip.ZipResolutionResult
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyGraphProvider
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.resolve
import java.nio.file.Path

public class ProductionBootInstance(
    override val location: Path,
    override val dependencyTypes: DependencyTypeContainer
) : BootInstance {
    override val componentGraph: SoftwareComponentGraph =
        initSoftwareComponentGraph(location resolve "cmpts", dependencyTypes, this)

    init {
        initMaven(
            dependencyTypes,
            location resolve "m2"
        )
    }

    override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
        return componentGraph.isCached(descriptor)
    }

    override fun cache(request: SoftwareComponentArtifactRequest, location: SoftwareComponentRepositorySettings) {
        val cacher = componentGraph.cacherOf(
            location,
        )

        cacher.cache(
            request
        )
    }

    override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
        descriptor: SoftwareComponentDescriptor,
        factoryType: Class<out ComponentFactory<T, I>>,
        configuration: T
    ): I {
        val it = componentGraph.get(descriptor).tapLeft { throw it }.orNull()!!

        check(factoryType.isInstance(it.factory))

        val factory = it.factory as? ComponentFactory<T, I>
            ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this.")

        return factory.new(configuration)
    }
}

public fun initMaven(
    types: DependencyTypeContainer,
    cache: Path
) {
    types.register(
        "simple-maven",
        createMavenProvider(cache)
    )
}


private fun initSoftwareComponentGraph(
    cache: Path,
    types: DependencyTypeContainer,
    boot: BootInstance
): SoftwareComponentGraph {
    return SoftwareComponentGraph(
        cache,
        CachingDataStore(SoftwareComponentDataAccess(cache)),
        BasicArchiveResolutionProvider(
            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.ZIP_RESOLVER
        ),
        types,
        boot, HashMap()
    )
}

public fun createMavenProvider(
    cache: Path,
): DependencyGraphProvider<*, *, *> {
    val dependencyGraph = createMavenDependencyGraph(cache)

    return object :
        DependencyGraphProvider<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
        override val name: String = "simple-maven"
        override val graph: DependencyGraph<SimpleMavenDescriptor, SimpleMavenRepositorySettings> =
            dependencyGraph

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

public fun createMavenDependencyGraph(
    cachePath: Path,
): MavenDependencyGraph {
    val resolutionProvider = object : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
        Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
        Archives.Resolvers.ZIP_RESOLVER,
    ) {}
    val graph = MavenDependencyGraph(
        cachePath,
        CachingDataStore(
            MavenDataAccess(cachePath)
        ),
        resolutionProvider
    )


    return graph
}

