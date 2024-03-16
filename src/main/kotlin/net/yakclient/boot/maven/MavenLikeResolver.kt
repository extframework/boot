package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.result
import net.yakclient.boot.archive.*
import net.yakclient.boot.util.mapOfNonNullValues
import net.yakclient.boot.util.requireKeyInDescriptor
import java.io.File
import java.nio.file.Path

public interface MavenLikeResolver<
        V : ArchiveNode<V>,
        M : SimpleMavenArtifactMetadata> :
    ArchiveNodeResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, V, SimpleMavenRepositorySettings, M> {

    override val auditor: ArchiveAccessAuditor
        get() = super.auditor.chain(MavenCollisionFixingAuditor())

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<SimpleMavenDescriptor> = result {
        SimpleMavenDescriptor(
            descriptor.requireKeyInDescriptor("group") { trace },
            descriptor.requireKeyInDescriptor("artifact") { trace },
            descriptor.requireKeyInDescriptor("version") { trace },
            descriptor["classifier"]
        )
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