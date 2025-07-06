package com.kaolinmc.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.boot.archive.ArchiveNode
import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.archive.ArchiveTrace
import com.kaolinmc.boot.archive.RegisterAuditor
import com.kaolinmc.boot.audit.Auditors
import com.kaolinmc.boot.constraint.registerConstraintNegotiator
import com.kaolinmc.boot.util.mapOfNonNullValues
import com.kaolinmc.boot.util.requireKeyInDescriptor
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

public interface MavenLikeResolver<
        V : ArchiveNode<SimpleMavenDescriptor>,
        M : SimpleMavenArtifactMetadata
        > :
    ArchiveNodeResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, V, SimpleMavenRepositorySettings, M>,
    RegisterAuditor {

    override fun register(auditors: Auditors): Auditors {
        return auditors.registerConstraintNegotiator(
            MavenConstraintNegotiator()
        )
    }

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): SimpleMavenDescriptor {
        return SimpleMavenDescriptor(
            descriptor.requireKeyInDescriptor("group") { trace },
            descriptor.requireKeyInDescriptor("artifact") { trace },
            descriptor.requireKeyInDescriptor("version") { trace },
            descriptor["classifier"]
        )
    }

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            descriptor.classifier ?: "",
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