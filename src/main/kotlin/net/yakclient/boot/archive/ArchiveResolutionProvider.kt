package net.yakclient.boot.archive

import com.durganmcbroom.jobs.*
import kotlinx.coroutines.Deferred
import net.yakclient.archives.*
import net.yakclient.archives.jpm.JpmResolutionResult
import net.yakclient.archives.zip.ZipResolutionResult
import java.nio.file.Path
import kotlin.io.path.exists

public interface ArchiveResolutionProvider<out R : ResolutionResult> {
    public fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): JobResult<R, ArchiveLoadException>
}

public open class BasicArchiveResolutionProvider<T : ArchiveReference, R : ResolutionResult>(
    protected val finder: ArchiveFinder<T>,
    protected val resolver: ArchiveResolver<T, R>,
) : ArchiveResolutionProvider<R> {
    override fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): JobResult<R, ArchiveLoadException> {
        if (!resource.exists()) return JobResult.Failure(ArchiveLoadException.ArchiveLoadFailed("Given path: '$resource' to archive does not exist!"))

        return runCatching {
            resolver.resolve(
                listOf(finder.find(resource)),
                classLoader,
                parents
            ).first()
        }.let {
            if (it.isFailure) JobResult.Failure(
                ArchiveLoadException.ArchiveLoadFailed(
                    it.exceptionOrNull()?.message ?: "Unable to determine the reason of failure."
                )
            )
            else JobResult.Success(it.getOrNull()!!)
        }
    }
}

public object JpmResolutionProvider : BasicArchiveResolutionProvider<ArchiveReference, JpmResolutionResult>(
    Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
    Archives.Resolvers.JPM_RESOLVER
)

public object ZipResolutionProvider : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
    Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
    Archives.Resolvers.ZIP_RESOLVER
)
