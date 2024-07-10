package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.*
import dev.extframework.boot.loader.*
import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

public abstract class DependencyResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        N : DependencyNode<N>,
        S : RepositorySettings,
        M : ArtifactMetadata<K, *>,
        >(
    private val parentClassLoader: ClassLoader,
    private val resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider
) : ArchiveNodeResolver<K, R, N, S, M> {

    override val nodeType: Class<in N> = DependencyNode::class.java

    override fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<N> = job {
        val parents: Set<N> = data.parents.mapTo(HashSet()) {
            helper.load(it)
        }

        val access = helper.newAccessTree {
            allDirect(parents)
        }

        val archive = data.resources["jar.jar"]?.let {
            resolutionProvider.resolve(
                it.path,
                { ref ->
                    ArchiveClassLoader(
                        ref,
                        access,
                        parentClassLoader
                    )
//                    IntegratedLoader(
//                        name = data.descriptor.name,
//                        sourceProvider = ArchiveSourceProvider(ref),
//                        resourceProvider = ArchiveResourceProvider(ref),
//                        classProvider = DelegatingClassProvider(access.targets.map { target ->
//                            target.relationship.classes
//                        }),
//                        sourceDefiner = {n, b, cl, d ->
//                            d(n, b, ProtectionDomain(CodeSource(ref.location.toURL(), arrayOf<Certificate>()), null, cl, null))
//                        },
//                        parent = parentClassLoader,
//                    )
                },
                parents.mapNotNullTo(HashSet(), DependencyNode<*>::archive)
            )().merge().archive
        }

        constructNode(
            data.descriptor,
            archive,
            parents,
            access,
        )
    }

    protected abstract fun constructNode(
        descriptor: K,
        handle: ArchiveHandle?,
        parents: Set<N>,
        accessTree: ArchiveAccessTree,
    ): N

    override fun cache(
        metadata: M,
        helper: ArchiveCacheHelper<K>
    ): Job<ArchiveData<K, CacheableArchiveResource>> = job {
        helper.withResource("jar.jar", metadata.resource)

        helper.newData(metadata.descriptor)
    }
}
