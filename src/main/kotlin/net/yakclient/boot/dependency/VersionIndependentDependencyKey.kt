package net.yakclient.boot.dependency

import net.yakclient.boot.archive.ArchiveKey

// Represents a key to a descriptor that excludes any version dependence.
// Should implement equals and hashcode.
public interface VersionIndependentDependencyKey : ArchiveKey