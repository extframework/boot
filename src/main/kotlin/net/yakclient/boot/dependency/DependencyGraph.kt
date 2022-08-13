package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.group.GroupedArtifactGraph
import net.yakclient.archives.*
import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.RepositoryArchiveGraph
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.toSafeResource
import java.nio.file.Path

public class DependencyGraph(
    path: Path,
    private val metadataProviders: List<DependencyMetadataProvider<*>>,
    private val artifactGraph: GroupedArtifactGraph,
    private val af: ArchiveFinder<*>,
    private val ar: ArchiveResolver<ArchiveReference, out ResolutionResult>,
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : RepositoryArchiveGraph<DependencyNode, DescriptorKey, DependencyData>(
    DependencyStore(
        path, metadataProviders
    )
) {
    private val _graph: MutableMap<DescriptorKey, DependencyNode> = HashMap()
    override val graph: Map<DescriptorKey, DependencyNode>
        get() = _graph.toMap()

    @JvmOverloads
    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>>,
        graph: ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<*, *, S, O>> = artifactGraph[provider]
            ?: throw IllegalArgumentException("Unknown Graph provider: '${provider::class.simpleName}'."),
        settings: S = graph.newRepoSettings(),
        settingConfigurer: S.() -> Unit = {}
    ): RepositoryGraphPopulator<O> = DependencyGraphPopulator(graph.resolverFor(settings.apply(settingConfigurer)))

    private fun <T : ArtifactMetadata.Descriptor> getProvider(desc: T): DependencyMetadataProvider<T> =
        metadataProviders.find { it.descriptorType.isInstance(desc) } as? DependencyMetadataProvider<T>
            ?: throw IllegalStateException("Descriptor: '${desc::class.qualifiedName}' has no registered metadata provider! You must provide one to use this type.")

    private inner class DependencyGraphPopulator<O : ArtifactResolutionOptions>(
        resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    ) : RepositoryGraphPopulator<O>(
        resolver
    ) {
        override fun load(name: String, options: O): DependencyNode? {
            val desc = resolver.descriptorOf(name) ?: return null

            val key = DescriptorKey(desc)
            return graph[key] ?: run {
                val local = LocalGraph()

                val data = store[key] ?: run {
                    val artifact = resolver.artifactOf(name, options) ?: return null
                    local.cache(artifact)
                    store[key]
                } ?: return null

                local.load(data)
            }
//            val key = desc.versionDependentKey()
//
//            return graph[desc.versionIndependentKey()] ?: load(store[key] ?: resolver.artifactOf(name, options)
//                ?.let(::cache)?.let { store[key] } ?: return null)
        }

        private inner class LocalGraph {
            private val localGraph: MutableMap<VersionIndependentDependencyKey, DependencyNode> = HashMap()

            private fun getBy(desc: ArtifactMetadata.Descriptor): DependencyNode? {
                val keyFor = getProvider(desc).keyFor(desc)
                return localGraph[keyFor] ?: _graph[DescriptorKey(desc)]?.also {
                    localGraph[keyFor] = it
                }
            }

            private fun putBy(desc: ArtifactMetadata.Descriptor, node: DependencyNode): DependencyNode {
                localGraph[getProvider(desc).keyFor(desc)] = node
                _graph[DescriptorKey(desc)] = node

                return node
            }

            fun load(data: DependencyData): DependencyNode {
                fun checkChild(data: DependencyData?): DependencyData {
                    return data
                        ?: throw IllegalStateException("Dependency cache invalidated! Please delete everything in directory: '${store.path()}'.")
                }

                fun DependencyNode.handleOrChildren(): Set<ArchiveHandle> = children.flatMapTo(HashSet()) { d ->
                    d.archive?.let(::setOf) ?: d.children.flatMapTo(HashSet()) { it.handleOrChildren() }
                }

                return getBy(data.key.desc) ?: run {
                    val children = data.children.map(store::get).map(::checkChild).mapTo(HashSet(), ::load)

                    val ref: ArchiveReference =
                        af.find(data.archive?.uri?.let(Path::of) ?: return DependencyNode(null, children))

                    val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }
                    val classloader = DependencyClassLoader(ref, handles, privilegeManager)

                    val handle = Archives.resolve(ref, classloader, ar, handles).archive

                    return putBy(data.key.desc, DependencyNode(handle, children))
                }
            }

            fun cache(artifact: Artifact) {
                val desc = artifact.metadata.desc
                val key = DescriptorKey(desc)

                if (!store.contains(key)) {
                    store.put(
                        key, DependencyData(key,
                            artifact.metadata.resource?.toSafeResource(),
                            artifact.children.map { DescriptorKey(it.metadata.desc) })
                    )

                    artifact.children.forEach(::cache)
                }
            }
        }
    }
}