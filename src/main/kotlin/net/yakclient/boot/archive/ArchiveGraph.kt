package net.yakclient.boot.archive

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.*
import net.yakclient.boot.dependency.ArchiveStubResolver
import net.yakclient.boot.store.DataStore
import java.util.logging.Logger

public abstract class ArchiveGraph<K: ArtifactRequest<*>, out V: ArchiveNode, in R: RepositorySettings>(
    public open val repositoryFactory: RepositoryFactory<R, *, *, *, *>
) {
    public abstract val graph: Map<*, V>
    protected val logger: Logger = Logger.getLogger(this::class.simpleName)

    public abstract fun loaderOf(settings: R) : ArchiveLoader<*>

    public abstract fun get(request: K) : Either<ArchiveLoadException, V>

    public abstract inner class ArchiveLoader<S: ArtifactStub<K, *>>(
        protected open val resolver: ResolutionContext<K, S, ArtifactReference<*, S>>,
    ) {
        public abstract fun load(request: K): Either<ArchiveLoadException, V>
    }
}