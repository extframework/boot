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
        data: ArchiveData<K, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<N> = job {
        val accessibleNodes = accessTree.targets
            .asSequence()
            .map(ArchiveTarget::relationship)
            .map(ArchiveRelationship::node)

        val archive = data.resources["jar.jar"]?.let {
            resolutionProvider.resolve(
                it.path,
                { ref ->
                    ArchiveClassLoader(
                        ref,
                        accessTree,
                        parentClassLoader
                    )
                },
                accessibleNodes
                    .filterIsInstance<ClassLoadedArchiveNode<*>>()
                    .mapTo(mutableSetOf(), ClassLoadedArchiveNode<*>::handle),

                helper.trace
            )().merge().archive
        }

        constructNode(
            data.descriptor,
            archive,
            accessibleNodes
                .filter { nodeType.isInstance(it) }
                .mapTo(mutableSetOf()) { it as N },
            accessTree,
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
    ): Job<Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> = job {
        helper.withResource("jar.jar", artifact.metadata.resource)

        helper.newData(
            artifact.metadata.descriptor,
            artifact.children.map {
                helper.cache(
                    it, this@DependencyResolver,
                )().merge()
            }
        )
    }
}
