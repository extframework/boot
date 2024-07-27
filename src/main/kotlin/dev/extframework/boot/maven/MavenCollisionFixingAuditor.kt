package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.archive.audit.ArchiveAccessAuditor
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.audit.prune

public class MavenCollisionFixingAuditor : ArchiveAccessAuditor {
    override fun audit(tree: ArchiveAccessTree): ArchiveAccessTree {
        var newTree = tree

        val unVersionedDescs = HashSet<String>()
        for (target in tree.targets) {
            val desc = target.descriptor
            if (desc !is SimpleMavenDescriptor) continue
            if (!unVersionedDescs.add("${desc.group}:${desc.artifact}")) {
                newTree = newTree.prune(target)
            }
        }

        return newTree
    }
}