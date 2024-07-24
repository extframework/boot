package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobContext
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.facet
import com.durganmcbroom.resources.Resource
import dev.extframework.boot.monad.AndMany
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import java.nio.file.Path
import kotlin.reflect.KClass

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
        M : ArtifactMetadata<K, *>> {
    public val name: String

    public val nodeType: Class<in V>
    public val metadataType: Class<M>

    public val auditor: ArchiveAccessAuditor
        get() = ArchiveAccessAuditor { tree -> tree }

    public val negotiator: ConstraintNegotiator<K, *>

    /**
     * Creates a resolution context for the given repository settings.
     *
     * @param settings the repository settings
     * @return a resolution context with the specified settings
     */
    public fun createContext(settings: S): ResolutionContext<R, *, M, *>

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
     * @return a Result object that encapsulates the deserialized instance of type K
     */
    public fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace,
    ): Result<K>

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
     * @return a [Job] representing the loading process of the archive
     */
    public fun load(
        data: AndMany<
                ArchiveData<K, CachedArchiveResource>,
                Tree<Tagged<
                        IArchive<*>,
                        ArchiveNodeResolver<*, *, *, *, *>
                        >>
                >,
        helper: ResolutionHelper
    ): Job<V>

    /**
     * Caches the archive specified by the given [metadata] using the provided [helper].
     *
     * @param metadata the metadata of the archive to cache
     * @param helper the cache helper used to cache the archive
     * @return a [Job] representing the caching process of the archive
     */
    public fun cache(
        artifact: Artifact<M>,
        helper: CacheHelper<K>
    ): Job<AndMany<ArchiveData<K, CacheableArchiveResource>, Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>>
}

/**
 * The ResolutionHelper interface provides helper methods for resolving and loading archive nodes.
 */
public interface ResolutionHelper {
    /**
     * Loads the archive specified by the given descriptor with the provided resolver from
     * the ArchiveGraph providing this helper.
     *
     * @param descriptor the descriptor of the archive to load
     * @param resolver the resolver used to load the archive
     * @return the loaded archive node
     */
    public fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<T>> load(
        iArchive: AndMany<IArchive<T>, Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>,
        resolver: ArchiveNodeResolver<T, *, N, *, *>,
    ): Job<N>

    /**
     * Retrieves the resolver for the specified name, descriptor type, and node type if registered from
     * the Archive graph providing this helper.
     *
     * @param name the name of the resolver
     * @param descType the class representing the artifact descriptor type
     * @param nodeType the class representing the archive node type
     * @return a [Result] encapsulating the resolver matching the specified criteria
     */
//    public fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<T>> getResolver(
//        name: String,
//        descType: Class<T>,
//        nodeType: Class<N>,
//    ): Result<ArchiveNodeResolver<T, *, N, *, *>>

    /**
     * Creates a new access tree for specifying direct dependencies and targets for archive resolution.
     *
     * @param scope a lambda function that defines the direct dependencies and targets for the access tree
     * @return the created ArchiveAccessTree
     */
    public fun newAccessTree(scope: AccessTreeScope.() -> Unit): ArchiveAccessTree

    /**
     * The AccessTreeScope interface defines the scope for specifying direct dependencies and targets for archive resolution.
     */
    public interface AccessTreeScope {
        public fun direct(dependency: ClassLoadedArchiveNode<*>)

        public fun allDirect(dependencies: Collection<ClassLoadedArchiveNode<*>>) {
            for (d in dependencies) direct(d)
        }

        public fun rawTarget(
            target: ArchiveTarget
        )
    }
}

/**
 * The CacheHelper interface represents a helper for caching artifacts from a cache.
 *
 * @param K the type of the artifact descriptor
 */
public interface CacheHelper<K : ArtifactMetadata.Descriptor> {
    public fun <
            D : ArtifactMetadata.Descriptor,
            M : ArtifactMetadata<D, *>,
            > cache(
        artifact: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): Job<AndMany<Tagged<ArchiveData<D, CacheableArchiveResource>, ArchiveNodeResolver<D, *, *, *, *>>, Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>>

    public fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, *>
            > resolveArtifact(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Job<Artifact<M>>

//    public fun <T : ArtifactRequest<*>, R : RepositorySettings> getResolver(
//        name: String,
//        requestType: Class<T>,
//        repositoryType: Class<R>,
//    ): Result<ArchiveNodeResolver<*, T, *, R, *>>

    public fun withResource(name: String, resource: Resource)

    public fun withResources(resources: Map<String, Resource>) {
        resources.forEach {
            withResource(it.key, it.value)
        }
    }

//    public fun withViewTree(scope: AccessTreeScope.() -> Unit)

//    public interface AccessTreeScope {
//        public fun direct(
//            parent: ArchiveData<*, *>,
//        )
//    }

    public fun newData(
        descriptor: K,
        parents: List<Tree<ArchiveData<*, *>>>
    ): AndMany<ArchiveData<K, CacheableArchiveResource>, Tree<ArchiveData<*, *>>>
}

public fun CacheHelper<*>.withResource(name: String, resource: Resource?) {
    if (resource == null) return
    withResource(name, resource)
}

public fun JobScope.trace(): ArchiveTrace = facet(ArchiveTrace)

//public data class CachedAccessTree(
//    val parents: List<ArchiveParent<*>>
//)


//
//public data class ArchiveParent<T : ArtifactMetadata.Descriptor> internal constructor(
//
//)


public data class ArchiveTrace(
    val descriptor: ArtifactMetadata.Descriptor,
    val parent: ArchiveTrace?
) : JobContext.Facet {
    override val key: JobContext.Key<ArchiveTrace> = ArchiveTrace
    public fun child(descriptor: ArtifactMetadata.Descriptor): ArchiveTrace = ArchiveTrace(descriptor, this)

    public fun isCircular(toCheck: List<ArtifactMetadata.Descriptor> = listOf()): Boolean {
        return toCheck.any { it == descriptor } || parent?.isCircular(toCheck + descriptor) == true
    }

    public fun toList(): List<ArtifactMetadata.Descriptor> {
        return (parent?.toList() ?: listOf()) + descriptor
    }

    override fun toString(): String {
        return toList().joinToString(separator = " -> ") { it.toString() }
    }

    public companion object : JobContext.Key<ArchiveTrace> {
        override val name: String = "Archive Trace"
    }
}