package net.yakclient.boot
//
//import com.durganmcbroom.artifact.resolver.ArtifactMetadata
//import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
//import net.yakclient.boot.archive.ArchiveGraph
//import net.yakclient.boot.archive.ArchiveNode
//
//public inline fun <O : ArtifactResolutionOptions, N : ArchiveNode, V : ArchiveData> ArchiveGraph<N, *, V>.ArchiveLoader<*, O>.load(
//    name: String,
//    options: O.() -> Unit = {}
//): N? {
//    return load(name, emptyOptions().apply(options))
//}
//
//public inline fun <O : ArtifactResolutionOptions, N : ArchiveNode, V : ArchiveData, D: ArtifactMetadata.Descriptor> ArchiveGraph<N, *, V>.ArchiveLoader<D, O>.load(
//    desc: D,
//    options: O.() -> Unit = {}
//): N? {
//    return load(desc, emptyOptions().apply(options))
//}