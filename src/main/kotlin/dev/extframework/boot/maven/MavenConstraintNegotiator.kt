package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.warning
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.constraint.Constrained
import dev.extframework.boot.constraint.ConstraintException
import dev.extframework.boot.constraint.ConstraintNegotiator
import dev.extframework.boot.constraint.ConstraintType

public class MavenConstraintNegotiator(
    private val throwIfClashing: Boolean = true
) : ConstraintNegotiator<SimpleMavenDescriptor> {
    override val descriptorType: Class<SimpleMavenDescriptor> = SimpleMavenDescriptor::class.java

    override fun negotiate(
        constraints: Set<Constrained<SimpleMavenDescriptor>>,

        trace: ArchiveTrace
    ): Job<SimpleMavenDescriptor> = job {
        val bound = constraints.filterTo(mutableSetOf()) {
            it.type == ConstraintType.BOUND
        }

        if (bound.size > 1) {
            if (throwIfClashing)
                throw ConstraintException.Conflicting(trace, constraints, bound)
            else
                warning(ConstraintException.Conflicting.conflictMessage(trace, constraints, bound))
        }

        if (bound.size == 1) return@job bound.first().descriptor

        constraints.maxByOrNull { sortMavenDescriptorVersion(it.descriptor) }!!.descriptor
    }


    override fun classify(descriptor: SimpleMavenDescriptor): Any {
        return "${descriptor.group}:${descriptor.artifact}"
    }
}