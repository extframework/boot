package com.kaolinmc.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.resources.Resource
import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.loader.*
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import kotlinx.coroutines.awaitAll

public abstract class DependencyResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        N : DependencyNode<K>,
        S : RepositorySettings,
        M : ArtifactMetadata<K, ArtifactMetadata.ParentInfo<R, S>>,
        >(
    private val parentClassLoader: ClassLoader,
    private val resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider
) : ArchiveNodeResolver<K, R, N, S, M> {
    override val nodeType: Class<in N> = DependencyNode::class.java

    override fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): N {
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
                    .mapNotNullTo(mutableSetOf(), ClassLoadedArchiveNode<*>::handle),

                helper.trace
            ).archive
        }

       return constructNode(
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

    protected abstract suspend fun M.resource() : Resource?

    override suspend fun cache(
        metadata: M,
        parents: List<Tree<Either<M, TaggedIArchive>>>,
        helper: CacheHelper<K>
    ): Tree<TaggedIArchive> {
        helper.withResource("jar.jar", metadata.resource())

        return helper.newData(
            metadata.descriptor,
            parents.mapAsync {
                helper.cache(
                    it, this@DependencyResolver,
                )
            }.awaitAll()
        )
    }
}
