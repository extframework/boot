package dev.extframework.boot.constraint

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.textifyTree
import dev.extframework.boot.util.toGraphable

public open class ConstraintException(
    trace: ArchiveTrace,
    message: String
) : ArchiveException(trace, message) {
    public class Conflicting(
        trace: ArchiveTrace,
        group: List<Constrained<*>>,
        clashing: List<Constrained<*>>
    ) : ConstraintException(
        trace,
        conflictMessage(trace, group, clashing)
    ) {
        public companion object {
            public fun conflictMessage(
                trace: ArchiveTrace,
                group: List<Constrained<*>>,
                clashing: List<Constrained<*>>
            ): String = "Trace: '$trace'" +
                    "\nconstrained group: " +
                    "\n   '$group' " +
                    "\nhas the following clashing members:" +
                    "\n   '$clashing'"
        }
    }

    public class ConstraintNotFound(
        trace: ArchiveTrace,
        negotiated: ArtifactMetadata.Descriptor,
        tree: Tree<IArchive<*>>
    ) : ConstraintException(
        trace,
        "Constraints have been successfully negotiated to: '$negotiated'. However, this constraint could not be found in the archive tree: \n${
            textifyTree(tree.toGraphable { it.descriptor.name })
        }"
    )
}