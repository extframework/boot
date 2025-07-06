package com.kaolinmc.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.kaolinmc.boot.audit.Auditors
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.`object`.ObjectContainer
import java.nio.file.Path

/**
 * A graph of archives with the capability to load and cache. This
 * graph contains the relationships and nodes between archives.
 *
 * Example:
 * ``` kotlin
 * runBlocking {
 *   val graph = ArchiveGraph.from(Paths.get("local-cache"))
 *
 *   val request = SimpleMavenArtifactRequest("com.example:example:1.0")
 *
 *   graph.cache(
 *      request,
 *      SimpleMavenRepositorySettings.mavenCentral(),
 *      // maven resolver
 *   )
 *
 *   val node = graph.get(
 *      request.descriptor,
 *      // maven resolver
 *   )
 *
 *   // ...
 * }
 * ```
 */
public interface ArchiveGraph {
    public val path: Path

    public var auditors: Auditors

    public val resolvers: ObjectContainer<ArchiveNodeResolver<*, *, *, *, *>>

    public val nodes: Map<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>>

    /**
     * Caches the specified artifact request in the given repository using the provided resolver.
     *
     * @param request The artifact request to be cached.
     * @param repository The repository in which the artifact request will be cached.
     * @param resolver The resolver used to cache the artifact request.
     * @return A Job that represents the caching process.
     */
    public suspend fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Tree<TaggedIArchive>

    /**
     * Retrieves the specified ArchiveNode based on the given descriptor and resolver.
     * The archive must already be cached via `ArchiveGraph#cache` or else this
     * method will throw.
     *
     * @param descriptor The descriptor of the ArchiveNode to retrieve.
     * @param resolver The resolver used to retrieve the ArchiveNode.
     * @return A Job that will eventually resolve the specified archive.
     */
    public suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): T

    /**
     * Unloads the given node and returns all the nodes that were unloaded by this.
     * Note that this method does not guarantee speed and that these values will not
     * be discarded until the return value of this method is garbage collected. This method
     * will also not provide unloading of any processes/daemons these nodes may have spawned.
     */
    public suspend fun unload(descriptor: ArtifactMetadata.Descriptor) : List<ArchiveNode<*>>

    public companion object {
        /**
         * Creates a new ArchiveGraph from the given Path.
         *
         * @param path The Path representing the location of the archive.
         * @return The newly created ArchiveGraph.
         */
        public fun from(path: Path): ArchiveGraph {
            return DefaultArchiveGraph(path)
        }
    }
}