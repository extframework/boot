package net.yakclient.boot.archive

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.*
import java.util.logging.Logger

public abstract class ArchiveGraph<K: ArtifactMetadata.Descriptor, out V: ArchiveNode, in R: RepositorySettings>(
    public open val repositoryFactory: RepositoryFactory<R, *, *, *, *>
) {
    public abstract val graph: Map<K, V>
    protected val logger: Logger = Logger.getLogger(this::class.simpleName)

    public abstract fun get(descriptor: K) : Either<ArchiveLoadException, V>

    public abstract fun cacherOf(settings: R) : ArchiveCacher<out ArtifactRequest<K>, *>

    public abstract fun isCached(descriptor: K): Boolean

    public abstract inner class ArchiveCacher<E: ArtifactRequest<K>, S: ArtifactStub<E, *>>(
        protected open val resolver: ResolutionContext<E, S, ArtifactReference<*, S>>,
    ) {
        public abstract fun cache(request: E): Either<ArchiveLoadException, Unit>
    }
}