package dev.extframework.boot.archive.audit

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.ArchiveTrace

public interface AuditContext {
    public val trace: ArchiveTrace

    public fun isLoaded(descriptor: ArtifactMetadata.Descriptor) : Boolean
}