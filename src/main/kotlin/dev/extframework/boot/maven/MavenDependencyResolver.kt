package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.Resource
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveResolutionProvider
import dev.extframework.boot.archive.ZipResolutionProvider
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyResolver
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

public open class MavenDependencyResolver(
    parentClassLoader: ClassLoader,
    resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider,
    factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>(
    parentClassLoader, resolutionProvider
), MavenLikeResolver<BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override val context: ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata> =
        MavenResolutionContext(factory)

    override fun constructNode(
        descriptor: SimpleMavenDescriptor,
        handle: ArchiveHandle?,
        parents: Set<BasicDependencyNode<SimpleMavenDescriptor>>,
        accessTree: ArchiveAccessTree
    ): BasicDependencyNode<SimpleMavenDescriptor> {
        return BasicDependencyNode(
            descriptor, handle, accessTree
        )
    }

    override suspend fun SimpleMavenArtifactMetadata.resource(): Resource? = jar()

    override val name: String = "simple-maven"
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val apiVersion: Int = 1

    private open class MavenResolutionContext(
        factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRepository>,
    ) : ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata>(
        factory
    ) {
//        val localContext = WithLocalContext(factory.createNew(SimpleMavenRepositorySettings.local()))

//        override fun getAndResolve(
//            request: SimpleMavenArtifactRequest
//        ): Job<Artifact<SimpleMavenArtifactMetadata>> = job {
//            super.getAndResolve(request)().getOrNull() ?: localContext.getAndResolve(request)().merge()
//        }

        override fun getAndResolveAsync(
            request: SimpleMavenArtifactRequest,
            candidates: List<SimpleMavenRepositorySettings>,
            trace: List<ArtifactMetadata.Descriptor>
        ): AsyncJob<Artifact<SimpleMavenArtifactMetadata>> {
            return super.getAndResolveAsync(request, candidates + SimpleMavenRepositorySettings.local(), trace)
        }
    }

//    private open class WithLocalContext(
//        repository: SimpleMavenArtifactRepository,
//    ) : ResolutionContext<
//            SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata>(
//        repository
//    ) {
//        private val local = SimpleMavenRepositorySettings.local()
//
////        override fun getAndResolveAsync(
////            metadata: SimpleMavenArtifactMetadata,
////            cache: MutableMap<SimpleMavenArtifactRequest, Deferred<Artifact<SimpleMavenArtifactMetadata>>>,
////            trace: List<ArtifactMetadata.Descriptor>
////        ): AsyncJob<Artifact<SimpleMavenArtifactMetadata>> = asyncJob {
////            coroutineScope {
////                val newChildren = metadata.parents
////                    .map { child ->
////                        if (trace.contains(child.request.descriptor)) throw ArtifactResolutionException.CircularArtifacts(
////                            trace + metadata.descriptor
////                        )
////
////                        cache[child.request] ?: async {
////                            val exceptions = mutableListOf<Throwable>()
////
////                            val childMetadata = (child.candidates + local).firstNotNullOfOrNull { candidate ->
////                                val childMetadata = repository.factory
////                                    .createNew(candidate)
////                                    .get(child.request)()
////
////                                childMetadata.getOrElse {
////                                    exceptions.add(it)
////                                    null
////                                }
////                            } ?: if (exceptions.all { it is MetadataRequestException.MetadataNotFound }) {
////                                throw ArtifactException.ArtifactNotFound(
////                                    child.request.descriptor,
////                                    child.candidates,
////                                    trace
////                                )
////                            } else {
////                                throw IterableException(
////                                    "Failed to resolve '${child.request.descriptor}'", exceptions
////                                )
////                            }
////
////                            getAndResolveAsync(childMetadata, cache, trace + child.request.descriptor)().merge()
////                        }.also {
////                            cache[child.request] = it
////                        }
////                    }
////
////                Artifact(
////                    metadata,
////                    newChildren.awaitAll(),
////                )
////            }
////        }
//    }
}