package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.group.ResolutionGroupConfig
import net.yakclient.archives.*
import net.yakclient.boot.RepositoryArchiveGraph
import net.yakclient.boot.archive.ArchiveDescriptor
import net.yakclient.boot.archive.ArchiveStore
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager

public class DependencyGraph(
    store: ArchiveStore,
    groupConfig: ResolutionGroupConfig,
    private val af: ArchiveFinder<ArchiveReference>,
    private val ar: net.yakclient.archives.ArchiveResolver<ArchiveReference, ResolutionResult>,
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : RepositoryArchiveGraph<DependencyNode>(store, groupConfig) {
    private val _graph: MutableMap<ArchiveDescriptor, DependencyNode> = HashMap()
    override val graph: Map<ArchiveDescriptor, DependencyNode>
        get() = _graph.toMap()

    @JvmOverloads
    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>>,
        graph: ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>> = groupGraph[provider]
            ?: throw IllegalArgumentException("Unknown Graph provider: '${provider::class.simpleName}'."),
        settings: S = graph.newRepoSettings(),
        settingConfigurer: S.() -> Unit = {}
    ): RepositoryGraphPopulator<O> = DependencyGraphPopulator(graph.resolverFor(settings.apply(settingConfigurer)))

    public fun load(artifact: Artifact): DependencyNode {
        fun DependencyNode.handleOrChildren(): Set<ArchiveHandle> = children.flatMapTo(HashSet()) { d ->
            d.archive?.let(::setOf) ?: d.children.flatMapTo(HashSet()) { it.handleOrChildren() }
        }

        val desc = ArtifactArchiveDescriptor(artifact.metadata.desc)

        return graph[desc] ?: run {
            val cached = store.cache(artifact.metadata)

            val children = artifact.children.mapTo(HashSet(), ::load)

            val ref: ArchiveReference = af.find(cached.path ?: return DependencyNode(null, children))

            val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }
            val classloader = DependencyClassLoader(ref, handles, privilegeManager)

            val handle = Archives.resolve(ref, classloader, ar, handles).archive

            DependencyNode(handle, children).also { _graph[desc] = it }
        }
    }

    private inner class DependencyGraphPopulator<O : ArtifactResolutionOptions>(
        resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    ) : RepositoryGraphPopulator<O>(
        resolver
    ) {
        override fun load(artifact: Artifact): DependencyNode? = this@DependencyGraph.load(artifact)
    }
}