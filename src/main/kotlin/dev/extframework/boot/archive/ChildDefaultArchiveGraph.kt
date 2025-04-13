package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.monad.Tagged
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.MutableMap.MutableEntry

public class ChildDefaultArchiveGraph(
    public val parentGraph: DefaultArchiveGraph,
) : DefaultArchiveGraph(
    parentGraph.path,
) {
    override val path: Path = parentGraph.path

    private val childMutable
            : MutableMap<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> =
        ConcurrentHashMap()

    override val mutable: MutableMap<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> =
        object :
            MutableMap<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> by childMutable {
            override fun get(key: ArtifactMetadata.Descriptor): Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>? {
                return parentGraph.theGraph[key] ?: childMutable[key]
            }

            override val entries: MutableSet<MutableEntry<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
                get() = (childMutable.entries + parentGraph.theGraph.entries).mapTo(HashSet()) {
                    object :
                        MutableEntry<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
                        override fun setValue(newValue: Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>): Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>> {
                            return put(it.key, newValue) ?: newValue
                        }

                        override val key: ArtifactMetadata.Descriptor = it.key
                        override val value: Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>> = it.value
                    }
                }

            override fun remove(key: ArtifactMetadata.Descriptor): Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>? {
                return childMutable.remove(key)
            }

            override val size: Int
                get() = childMutable.size + parentGraph.theGraph.size
            override val keys: MutableSet<ArtifactMetadata.Descriptor>
                get() = (childMutable.keys + parentGraph.theGraph.keys).toMutableSet()
            override val values: MutableCollection<Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>>
                get() = (childMutable.values + parentGraph.theGraph.values).toMutableSet()
        }

    private var childAuditors: Auditors = Auditors()
    override var auditors: Auditors
        get() = parentGraph.auditors.chainAll(childAuditors)
        set(value) {
            val map = value.auditors.mapValues { (cls, list) ->
                list.filterNot {
                    parentGraph.auditors.auditors[cls]?.contains(it) == true
                }
            }.filter {
                it.value.isNotEmpty()
            }

            childAuditors = Auditors(map)
        }

    private val childResolvers = ObjectContainerImpl<ArchiveNodeResolver<*, *, *, *, *>>()
    override val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *>>
        get() = ObjectContainerImpl((parentGraph.theResolvers.objects() + childResolvers.objects()).toMutableMap())
}