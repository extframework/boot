package com.kaolinmc.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.resources.Resource
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.`object`.ObjectContainer
import java.nio.file.Path

/**
 * The ArchiveNodeResolver interface represents a resolver for archive nodes.
 *
 * @param K the type of the artifact descriptor
 * @param R the type of the artifact request
 * @param V the type of the archive node
 * @param S the type of the repository settings
 * @param M the type of the artifact metadata
 */
public interface ArchiveNodeResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        V : ArchiveNode<K>,
        S : RepositorySettings,
        M : ArtifactMetadata<K, ArtifactMetadata.ParentInfo<R, S>>> : ObjectContainer.IDed {
    override val id: String

    public val nodeType: Class<in V>
    public val metadataType: Class<M>

    public val apiVersion: Int
        get() = 0

    /**
     * A resolution context for the given repository settings.
     */
    public val factory: RepositoryFactory<S, ArtifactRepository<S, R, M>>

    /**
     * Serializes the given artifact descriptor into a map of key-value pairs.
     *
     * @param descriptor the artifact descriptor to serialize
     * @return a map containing the serialized key-value pairs of the descriptor
     */
    public fun serializeDescriptor(
        descriptor: K
    ): Map<String, String>

    /**
     * Deserializes the given descriptor map into an instance of type K, using the specified trace.
     *
     * @param descriptor the map containing the serialized key-value pairs of the descriptor
     * @param trace the archive trace that provides context for the deserialization
     * @return the deserialized instance of type K
     */
    public fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace,
    ): K

    /**
     * Returns the path for the given descriptor, classifier, and type. Will be resolved against
     * the base directory, this means absolute paths can technically work but, for most cases
     * just use a relative path (not starting with /)
     *
     * @param descriptor the descriptor of the artifact
     * @param classifier the classifier of the artifact
     * @param type the type of the artifact
     * @return the path for the given descriptor, classifier, and type
     */
    public fun pathForDescriptor(
        descriptor: K,
        classifier: String,
        type: String
    ): Path

    /**
     * Loads the archive specified by the given [data] with the provided [helper].
     *
     * @param data the archive data containing the descriptor and resources of the archive to load
     * @param helper the resolution helper used to load the archive
     */
    public fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): V

    /**
     * Caches the archive specified by the given [metadata] using the provided [helper].
     *
     * @see Artifact
     *
     * @param metadata the metadata of the archive to cache
     * @param parents the list of parents for this artifact. If the parent is not already cached its type will be of 'metadata', otherwise it will be a tagged archive.
     * @param helper the cache helper used to cache the archive
     */
    public suspend fun cache(
        metadata: M,
        parents: List<Tree<Either<M, TaggedIArchive>>>,
        helper: CacheHelper<K>
    ): Tree<TaggedIArchive>
}

public typealias TaggedIArchive = Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>

/**
 * The ResolutionHelper interface provides helper methods for resolving and loading archive nodes.
 */
public interface ResolutionHelper {
    public val trace: ArchiveTrace
}

/**
 * The CacheHelper interface represents a helper for caching artifacts from a cache.
 *
 * @param K the type of the artifact descriptor
 */
public interface CacheHelper<K : ArtifactMetadata.Descriptor> {
    public val trace: ArchiveTrace

    /**
     * Cache an entire tree given a resolver.
     */
    public suspend fun <
            D : ArtifactMetadata.Descriptor,
            M : ArtifactMetadata<D, *>,
            > cache(
        artifact: Tree<Either<M, TaggedIArchive>>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): Tree<TaggedIArchive>

    /**
     * Load an artifact tree.
     */
    public suspend fun <
            D: ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            > cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, *>
    ): Tree<TaggedIArchive>

    /**
     * Add a resource to the archive data being built.
     */
    public fun withResource(name: String, resource: Resource)

    /**
     * Add multiple resources to the archive data being built.
     */
    public fun withResources(resources: Map<String, Resource>) {
        resources.forEach {
            withResource(it.key, it.value)
        }
    }

    /**
     * Construct the data.
     */
    public suspend fun newData(
        descriptor: K,
        parents: List<Tree<TaggedIArchive>>
    ): Tree<TaggedIArchive>
}

/**
 * Conditionally add a resource based on if its null or not.
 */
public fun CacheHelper<*>.withResource(name: String, resource: Resource?) {
    if (resource == null) return
    withResource(name, resource)
}

