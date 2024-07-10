package dev.extframework.boot.archive

import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.loader.*
import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

public class ArchiveClassLoader(
    archive: ArchiveReference,
    public val accessTree: ArchiveAccessTree,
    parent: ClassLoader
) : IntegratedLoader(
    name = accessTree.descriptor.name,
    classProvider = DelegatingClassProvider(accessTree.targets.map { it.relationship.classes }),
    sourceProvider = ArchiveSourceProvider(archive),
    resourceProvider = ArchiveResourceProvider(archive),
    sourceDefiner = {n, b, cl, d ->
        d(n, b, ProtectionDomain(CodeSource(archive.location.toURL(), arrayOf<Certificate>()), null, cl, null))
    },
    parent = parent,
)