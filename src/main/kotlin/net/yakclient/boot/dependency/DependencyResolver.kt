package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.*
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.util.firstNotFailureOf
import net.yakclient.boot.util.toSafeResource
import kotlin.reflect.KClass

public abstract class DependencyResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        S : RepositorySettings,
        RStub : RepositoryStub,
        M : ArtifactMetadata<K, *>>(
    private val archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : ArchiveNodeResolver<K, R, DependencyNode, S, RStub, M> {
    override val nodeType: KClass<DependencyNode> = DependencyNode::class
    override suspend fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<DependencyNode, ArchiveException> = jobScope {
        val children: Set<DependencyNode> = data.children.mapTo(HashSet()) {
            resolver.load(it.descriptor as K, this@DependencyResolver)
        }

        val archive = data.resources["jar.jar"]?.let {
            val childHandles = children.flatMap { it.handleOrChildren() }.toSet()
            archiveResolver.resolve(
                it.path,
                { ref ->
                    DependencyClassLoader(ref, childHandles, privilegeManager)
                },
                childHandles
            ).attempt().archive
        }

        DependencyNode(
            archive,
            children,
            data.descriptor
        )
    }

    override suspend fun cache(
        ref: ArtifactReference<M, ArtifactStub<R, RStub>>,
        helper: ArchiveCacheHelper<RStub, S>
    ): JobResult<ArchiveData<K, CacheableArchiveResource>, ArchiveException> = jobScope {
        ArchiveData(
            ref.metadata.descriptor,
            ref.metadata.resource?.let {
                mapOf("jar.jar" to CacheableArchiveResource(it.toSafeResource()))
            } ?: mapOf(),
            ref.children.map { stub ->
                val firstNotFailureOf = stub.candidates.firstNotFailureOf {
                    helper.cache(
                        stub.request,
                        helper.resolve(it).casuallyAttempt(),
                        this@DependencyResolver
                    )
                }
                firstNotFailureOf.attempt()
            }
        )
    }
}
