package com.kaolinmc.boot.archive

import com.kaolinmc.archives.*
import com.kaolinmc.archives.zip.ZipResolutionResult
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.exists

public interface ArchiveResolutionProvider<out R : ResolutionResult> {
    public fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,

        trace: ArchiveTrace,
    ): R
}

public open class BasicArchiveResolutionProvider<T : ArchiveReference, R : ResolutionResult>(
    protected val finder: ArchiveFinder<T>,
    protected val resolver: ArchiveResolver<T, R>,
) : ArchiveResolutionProvider<R> {
    override fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,

        trace: ArchiveTrace,
    ): R {
        if (!resource.exists()) throw ArchiveException.ArchiveLoadFailed(
            FileNotFoundException(resource.toString()),
            trace
        )

        return runCatching {
            resolver.resolve(
                listOf(finder.find(resource)),
                classLoader,
                parents
            ).first()
        }.let {
            if (it.isFailure)
                throw ArchiveException.ArchiveLoadFailed(
                    it.exceptionOrNull()!!, trace
                )
            else it.getOrNull()!!
        }
    }
}

public object ZipResolutionProvider : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
    Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
    Archives.Resolvers.ZIP_RESOLVER
)
