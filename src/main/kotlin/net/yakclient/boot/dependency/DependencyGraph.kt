package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.group.GroupedArtifactGraph
import net.yakclient.archives.*
import net.yakclient.boot.ArtifactArchiveKey
import net.yakclient.boot.RepositoryArchiveGraph
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.toSafeResource
import java.nio.file.Path

public class DependencyGraph(
    store: DependencyStore,
    private val artifactGraph: GroupedArtifactGraph,
    private val af: ArchiveFinder<*>,
    private val ar: ArchiveResolver<ArchiveReference, out ResolutionResult>,
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : RepositoryArchiveGraph<DependencyNode, DependencyData>(store) {
    private val _graph: MutableMap<ArtifactArchiveKey, DependencyNode> = HashMap()
    override val graph: Map<ArtifactArchiveKey, DependencyNode>
        get() = _graph.toMap()

    @JvmOverloads
    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>>,
        graph: ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>> = artifactGraph[provider]
            ?: throw IllegalArgumentException("Unknown Graph provider: '${provider::class.simpleName}'."),
        settings: S = graph.newRepoSettings(),
        settingConfigurer: S.() -> Unit = {}
    ): RepositoryGraphPopulator<O> = DependencyGraphPopulator(graph.resolverFor(settings.apply(settingConfigurer)))

//    public fun load(artifact: Artifact): DependencyNode {
//        fun DependencyNode.handleOrChildren(): Set<ArchiveHandle> = children.flatMapTo(HashSet()) { d ->
//            d.archive?.let(::setOf) ?: d.children.flatMapTo(HashSet()) { it.handleOrChildren() }
//        }
//
//        val key = ArtifactArchiveKey(artifact.metadata.desc)
//
//        return graph[key] ?: run {
//            store.put(
//                key, DependencyData(
//                    key,
//                    artifact.metadata.resource?.toSafeResource(),
//                    artifact.children.map { ArtifactArchiveKey(it.metadata.desc) })
//            )
//
//            val data = store[key]!!
//
//            val children = artifact.children.mapTo(HashSet(), ::load)
//
//            val ref: ArchiveReference =
//                af.find(data.archive?.uri?.let(Path::of) ?: return DependencyNode(null, children))
//
//            val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }
//            val classloader = DependencyClassLoader(ref, handles, privilegeManager)
//
//            val handle = Archives.resolve(ref, classloader, ar, handles).archive
//
//            DependencyNode(handle, children).also { _graph[key] = it }
//        }
//    }

    private inner class DependencyGraphPopulator<O : ArtifactResolutionOptions>(
        resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    ) : RepositoryGraphPopulator<O>(
        resolver
    ) {
        override fun load(data: DependencyData): DependencyNode {
            fun checkChild(data: DependencyData?): DependencyData {
                return data
                    ?: throw IllegalStateException("Dependency cache invalidated! Please delete everything in directory: '${store.path()}'.")
            }

            fun DependencyNode.handleOrChildren(): Set<ArchiveHandle> = children.flatMapTo(HashSet()) { d ->
                d.archive?.let(::setOf) ?: d.children.flatMapTo(HashSet()) { it.handleOrChildren() }
            }

            val children = data.children
                .map(store::get)
                .map(::checkChild)
                .mapTo(HashSet(), ::load)

            val ref: ArchiveReference =
                af.find(data.archive?.uri?.let(Path::of) ?: return DependencyNode(null, children))

            val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }
            val classloader = DependencyClassLoader(ref, handles, privilegeManager)

            val handle = Archives.resolve(ref, classloader, ar, handles).archive

            return DependencyNode(handle, children).also { _graph[data.key] = it }
        }

        override fun cache(artifact: Artifact) {
            val key = ArtifactArchiveKey(artifact.metadata.desc)

            store.put(
                key, DependencyData(
                    key,
                    artifact.metadata.resource?.toSafeResource(),
                    artifact.children.map { ArtifactArchiveKey(it.metadata.desc) })
            )

            artifact.children.forEach(::cache)
        }
    }
}