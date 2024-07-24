package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.*

public data class MavenConstraint(
    override val descriptor: SimpleMavenDescriptor,
    override val type: ConstraintType
) : Constraint<SimpleMavenDescriptor>

public class MavenConstraintNegotiator : ConstraintNegotiator<SimpleMavenDescriptor,  MavenConstraint> {
    override val constraintType: Class<MavenConstraint>
        get() = TODO("Not yet implemented")

    override fun negotiate(group: List<MavenConstraint>): ArtifactMetadata.Descriptor {
        TODO("Not yet implemented")
    }

    override fun analyzeAccessFor(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        helper: ConstraintNegotiator.ConstraintResolutionHelper
    ): Job<ArchiveViewNode> {
        val path  = data.resources["jar.jar"]!!.path
        val archive = Archives.find(path, Archives.Finders.ZIP_FINDER)

        helper.resolveParentView()
    }

    override fun classifyConstraint(constraint: MavenConstraint): Any {
        TODO("Not yet implemented")
    }

}