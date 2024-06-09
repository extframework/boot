package dev.extframework.boot.archive

import com.durganmcbroom.jobs.*
import dev.extframework.archives.*
import dev.extframework.archives.jpm.JpmResolutionResult
import dev.extframework.archives.zip.ZipResolutionResult
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.exists

public interface ArchiveResolutionProvider<out R : ResolutionResult> {
    public fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): Job<R>
}

public open class BasicArchiveResolutionProvider<T : ArchiveReference, R : ResolutionResult>(
    protected val finder: ArchiveFinder<T>,
    protected val resolver: ArchiveResolver<T, R>,
) : ArchiveResolutionProvider<R> {
    override fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
    ): Job<R> = job(JobName("Load archive: '$resource'")) {
        if (!resource.exists()) throw ArchiveException.ArchiveLoadFailed(FileNotFoundException(resource.toString()), trace())

        runCatching {
            resolver.resolve(
                listOf(finder.find(resource)),
                classLoader,
                parents
            ).first()
        }.let {
            if (it.isFailure)
                throw ArchiveException.ArchiveLoadFailed(
                    it.exceptionOrNull()!!, facet(ArchiveTrace)
                )
            else it.getOrNull()!!
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
