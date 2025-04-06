package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob

//public open class GraphAwareResolutionContext<
//        S : RepositorySettings,
//        R : ArtifactRequest<*>,
//        M : ArtifactMetadata<*, ArtifactMetadata.ParentInfo<R, S>>
//        >(
//    factory: RepositoryFactory<S, ArtifactRepository<S, R, M>>,
//    protected val resolver: ArchiveNodeResolver<*, R, *, S, M>,
//    protected val graph: ArchiveGraph,
//) : ResolutionContext<S, R, M>(
//    factory
//) {
//    override fun getAndResolveAsync(
//        request: R,
//        candidates: List<S>,
//        trace: List<ArtifactMetadata.Descriptor>
//    ): AsyncJob<Artifact<M>> = asyncJob {
//        val node =
//            graph.get(request.descriptor, resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)()
//
//        if (!node.isFailure) {
//            return@asyncJob buildArtifact(node.merge())
//        }
//
//        super.getAndResolveAsync(request, candidates, trace)().merge()
//    }
//
//    protected open fun buildArtifact(
//        node: ArchiveNode<*>
//    ): Artifact<M> {
//        val parents = node.access.targets
//            .map { it.relationship }
//            .filterIsInstance<ArchiveRelationship.Direct>()
//            .map { it.node }
//
//        return Artifact(
//            // This will fail type checks obviously
//            ArtifactMetadata(node.descriptor, listOf()) as M,
//            parents.map(::buildArtifact)
//        )
//    }
//}