package com.kaolinmc.boot.constraint

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.kaolinmc.boot.archive.ArchiveException
import com.kaolinmc.boot.archive.ArchiveTrace
import com.kaolinmc.boot.archive.IArchive
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.textifyTree
import com.kaolinmc.boot.util.toGraphable

public open class ConstraintException(
    trace: ArchiveTrace,
    message: String
) : ArchiveException(trace, message) {
    public class Conflicting(
        trace: ArchiveTrace,
        group: Set<Constrained<*>>,
        clashing: Set<Constrained<*>>
    ) : ConstraintException(
        trace,
        conflictMessage(trace, group, clashing)
    ) {
        public companion object {
            public fun conflictMessage(
                trace: ArchiveTrace,
                group: Set<Constrained<*>>,
                clashing: Set<Constrained<*>>
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