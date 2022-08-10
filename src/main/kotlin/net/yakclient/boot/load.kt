package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
import net.yakclient.boot.archive.ArchiveData
import net.yakclient.boot.archive.ArchiveNode

public inline fun <O : ArtifactResolutionOptions, N : ArchiveNode, V : ArchiveData> RepositoryArchiveGraph<N, V>.RepositoryGraphPopulator<O>.load(
    name: String,
    options: O.() -> Unit = {}
): N? {
    return load(name, emptyOptions().apply(options))
}