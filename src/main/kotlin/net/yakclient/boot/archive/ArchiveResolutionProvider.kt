package net.yakclient.boot.archive

import arrow.core.Either
import arrow.core.continuations.either
import net.yakclient.archives.*
import net.yakclient.archives.jpm.JpmResolutionResult
import java.nio.file.Path
import kotlin.io.path.exists

public interface ArchiveResolutionProvider<out R : ResolutionResult> {
    public fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): Either<ArchiveLoadException, R>
}

public open class BasicArchiveResolutionProvider<T : ArchiveReference, R : ResolutionResult>(
    protected val finder: ArchiveFinder<T>,
    protected val resolver: ArchiveResolver<T, R>,
) : ArchiveResolutionProvider<R> {
    override fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): Either<ArchiveLoadException, R> = either.eager {
        ensure(resource.exists()) { ArchiveLoadException.ArchiveLoadFailed("Given path: '$resource' to archive does not exist!") }

        Either.catch {
            resolver.resolve(
                listOf(finder.find(resource)),
                classLoader,
                parents
            ).first()
        }.mapLeft { ArchiveLoadException.ArchiveLoadFailed(it.message ?: "Unable to determine the reason of failure.") }
            .bind()
    }
}

//public object JpmResolutionProvider : BasicArchiveResolutionProvider<ArchiveReference, JpmResolutionResult>(
//    Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
//    Archives.Resolvers.JPM_RESOLVER
//)
