package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobResult
import kotlinx.coroutines.Deferred
import java.util.logging.Logger


public abstract class ArchiveGraph<K: ArtifactMetadata.Descriptor, out V: ArchiveNode, in R: RepositorySettings>(
    public open val repositoryFactory: RepositoryFactory<R, *, *, *, *>
) {
    public abstract val graph: Map<K, V>

    public abstract suspend fun get(descriptor: K) : JobResult<V, ArchiveLoadException>

    public abstract fun cacherOf(settings: R) : ArchiveCacher<out ArtifactRequest<K>, *>

    public abstract fun isCached(descriptor: K): Boolean

    public abstract inner class ArchiveCacher<E: ArtifactRequest<K>, S: ArtifactStub<E, *>>(
        protected open val resolver: ResolutionContext<E, S, ArtifactReference<*, S>>,
    ) {
        public abstract suspend fun cache(request: E): JobResult<Unit, ArchiveLoadException>
    }
}