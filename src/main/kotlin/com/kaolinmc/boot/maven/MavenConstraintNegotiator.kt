package com.kaolinmc.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.kaolinmc.boot.archive.ArchiveTrace
import com.kaolinmc.boot.constraint.Constrained
import com.kaolinmc.boot.constraint.ConstraintException
import com.kaolinmc.boot.constraint.ConstraintNegotiator
import com.kaolinmc.boot.constraint.ConstraintType
import com.kaolinmc.boot.getLogger

public class MavenConstraintNegotiator(
    private val throwIfClashing: Boolean = false
) : ConstraintNegotiator<SimpleMavenDescriptor> {
    private val logger = getLogger()
    override val descriptorType: Class<SimpleMavenDescriptor> = SimpleMavenDescriptor::class.java

    override fun negotiate(
        constraints: Set<Constrained<SimpleMavenDescriptor>>,

        trace: ArchiveTrace
    ): SimpleMavenDescriptor {
        val bound = constraints.filterTo(mutableSetOf()) {
            it.type == ConstraintType.BOUND
        }

        if (bound.size > 1) {
            if (throwIfClashing)
                throw ConstraintException.Conflicting(trace, constraints, bound)
            else
                logger.warning(ConstraintException.Conflicting.conflictMessage(trace, constraints, bound))
        }

        if (bound.size == 1) return bound.first().descriptor

        return constraints.maxByOrNull { sortMavenDescriptorVersion(it.descriptor) }!!.descriptor
    }


    override fun classify(descriptor: SimpleMavenDescriptor): Any {
        return "${descriptor.group}:${descriptor.artifact}"
    }
}