package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.*
import dev.extframework.boot.loader.*
import dev.extframework.boot.monad.AndMany
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.mapItem

public abstract class DependencyResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        N : DependencyNode<K>,
        S : RepositorySettings,
        M : ArtifactMetadata<K, *>,
        >(
    private val parentClassLoader: ClassLoader,
    private val resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider
) : ArchiveNodeResolver<K, R, N, S, M> {
    override val nodeType: Class<in N> = DependencyNode::class.java

    override fun load(
        data: AndMany<ArchiveData<K, CachedArchiveResource>, Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>,
        helper: ResolutionHelper
    ): Job<N> = job {
        val parents: Set<ArchiveNode<ArtifactMetadata.Descriptor>> = data.parents.mapTo(mutableSetOf()) {
            helper.load(
                it as AndMany<IArchive<ArtifactMetadata.Descriptor>, Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>,
                it.item.tag as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>
            )().merge()
        }

        val access = helper.newAccessTree {
            allDirect(
                parents as Collection<ClassLoadedArchiveNode<*>>
            )
        }

        val archive = data.item.resources["jar.jar"]?.let {
            resolutionProvider.resolve(
                it.path,
                { ref ->
                    ArchiveClassLoader(
                        ref,
                        access,
                        parentClassLoader
                    )
                },
                parents.mapNotNullTo(HashSet(), DependencyNode<*>::handle)
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
        artifact: Artifact<M>,
        helper: CacheHelper<K>
    ): Job<AndMany<ArchiveData<K, CacheableArchiveResource>, Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>> {

    }

    override fun cache(
        metadata: M,
        helper: CacheHelper<K>
    ): Job<ArchiveData<K, CacheableArchiveResource>> = job {
        helper.withResource("jar.jar", metadata.resource)

        helper.newData(metadata.descriptor)
    }
}
