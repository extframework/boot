package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.RepositoryStub
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveNodeResolver
import net.yakclient.boot.util.mapOfNonNullValues
import net.yakclient.boot.util.requireKeyInDescriptor
import java.io.File
import java.nio.file.Path

public interface MavenLikeResolver<
        R : ArtifactRequest<SimpleMavenDescriptor>,
        V : ArchiveNode<V>,
        S : RepositorySettings,
        RStub : RepositoryStub,
        M : ArtifactMetadata<SimpleMavenDescriptor, *>> :
    ArchiveNodeResolver<SimpleMavenDescriptor, R, V, S, RStub, M> {

    override suspend fun deserializeDescriptor(
        descriptor: Map<String, String>
    ): JobResult<SimpleMavenDescriptor, ArchiveException> = jobScope {
        try {
            SimpleMavenDescriptor(
                descriptor.requireKeyInDescriptor("group"),
                descriptor.requireKeyInDescriptor("artifact"),
                descriptor.requireKeyInDescriptor("version"),
                descriptor["classifier"]
            )
        } catch (e: ArchiveException) {
            fail(e)
        }
    }

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path.of(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            "${descriptor.artifact}-${descriptor.version}-$classifier.$type"
        )
    }

    override fun serializeDescriptor(descriptor: SimpleMavenDescriptor): Map<String, String> {
        return mapOfNonNullValues(
            "group" to descriptor.group,
            "artifact" to descriptor.artifact,
            "version" to descriptor.version,
            "classifier" to descriptor.classifier
        )
    }
}