package dev.extframework.boot.archive

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.*
import dev.extframework.archives.module.JpmFinder
import dev.extframework.archives.module.JpmResolutionResult
import dev.extframework.archives.module.JpmResolver
import dev.extframework.archives.zip.ZipResolutionResult
import java.nio.file.Path

// Use zip resolution result because the jpm one has a bunch of module stuff thats not guaranteed to be provided
public object JpmResolutionProvider : ArchiveResolutionProvider<ZipResolutionResult> {
    private val jpmProvider = BasicArchiveResolutionProvider(
        JpmFinder as ArchiveFinder<ArchiveReference>,
        JpmResolver as ArchiveResolver<ArchiveReference, JpmResolutionResult>
    )
    private val fallbackProvider = ZipResolutionProvider

    override fun resolve(
        resource: Path,
        classLoader: ClassLoaderProvider<ArchiveReference>,
        parents: Set<ArchiveHandle>,
        trace: ArchiveTrace
    ): Job<ZipResolutionResult> = job {
        val jpmResult = runCatching {
            val result = jpmProvider.resolve(resource, classLoader, parents, trace)().merge()

            ZipResolutionResult(
                result.archive
            )
        }.getOrNull()

        jpmResult ?: fallbackProvider.resolve(resource, classLoader, parents, trace)().merge()
    }
}