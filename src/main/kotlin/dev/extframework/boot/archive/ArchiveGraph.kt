package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * A graph of archives with the capability to load and cache more. This
 * graph contains the relationships and nodes between archives.
 *
 * Example:
 * ``` kotlin
 * launch {
 *   val graph = ArchiveGraph(...)
 *
 *   val request = SimpleMavenArtifactRequest("com.example:example:1.0")
 *
 *   graph.cache(
 *      request,
 *      SimpleMavenRepositorySettings.mavenCentral(),
 *      // maven resolver
 *   )().merge()
 *
 *   val node = graph.get(
 *      request.descriptor,
 *      // maven resolver
 *   )().merge()
 *
 *   // ...
 * }
 * ```
 */
public interface ArchiveGraph {
    public val path: Path

    /**
     * Registers a resolver with the ArchiveGraph.
     *
     * @param resolver The resolver to register.
     */
    public fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>)

    /**
     * Retrieves the ArchiveNodeResolver with the specified name.
     *
     * @param name The name of the ArchiveNodeResolver to retrieve.
     * @return The ArchiveNodeResolver with the specified name if found, or null otherwise.
     */
    public fun getResolver(name: String): ArchiveNodeResolver<*, *, *, *, *>?

    /**
     * Caches the specified artifact request in the given repository using the provided resolver.
     *
     * @param request The artifact request to be cached.
     * @param repository The repository in which the artifact request will be cached.
     * @param resolver The resolver used to cache the artifact request.
     * @return A Job that represents the caching process.
     */
    public fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cacheAsync(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): AsyncJob<Unit>

    /**
     * @see cacheAsync
     * Runs the job in a blocking coroutine scope.
     *
     * @param request The artifact request to be cached.
     * @param repository The repository in which the artifact request will be cached.
     * @param resolver The resolver used to cache the artifact request.
     * @return A Job that represents the caching process.
     */
    public fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Job<Unit> = job {
        runBlocking(Dispatchers.IO) {
            cacheAsync(request, repository, resolver)().merge()
        }
    }

    /**
     * Retrieves the specified ArchiveNode based on the given descriptor and resolver.
     * The archive must already be cached via `ArchiveGraph#cache` or else this
     * method will throw.
     *
     * @param descriptor The descriptor of the ArchiveNode to retrieve.
     * @param resolver The resolver used to retrieve the ArchiveNode.
     * @return A Job that will eventually resolve the specified archive.
     */
    public fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> getAsync(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): AsyncJob<T>

    /**
     * @see getAsync
     * Runs the job in a blocking coroutine scope.
     *
     * @param descriptor The descriptor of the ArchiveNode to retrieve.
     * @param resolver The resolver used to retrieve the ArchiveNode.
     * @return A Job that will eventually resolve the specified archive.
     */
    public fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): Job<T> = job {
        runBlocking(Dispatchers.IO) {
            getAsync(descriptor, resolver)().merge()
        }
    }

    /**
     * Retrieves an already loaded ArchiveNode with no guarantee of type. The
     * returned node will always match the given descriptor or will be null.
     *
     * @param descriptor The descriptor of the ArchiveNode to retrieve.
     * @return The ArchiveNode with the specified descriptor or null
     */
    public fun getNode(
        descriptor: ArtifactMetadata.Descriptor,
    ): ArchiveNode<*>?

    /**
     * Checks if an ArchiveNode with the specified descriptor is already loaded.
     *
     * @param descriptor The descriptor of the ArchiveNode to check.
     * @return true if an ArchiveNode with the specified descriptor is loaded, false otherwise.
     */
    public fun loaded(
        descriptor: ArtifactMetadata.Descriptor,
    ): Boolean = getNode(descriptor) != null

    /**
     * Returns all the loaded archives by this graph.
     *
     * @return The collection of ArchiveNode objects.
     */
    public fun nodes(): Collection<ArchiveNode<*>>

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