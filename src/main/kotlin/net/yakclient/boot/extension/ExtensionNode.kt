package net.yakclient.boot.extension

import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode

public interface ExtensionNode : ArchiveNode {
    public val dependencies: Set<DependencyNode>
    public val extension: Container<ExtensionProcess>
}
