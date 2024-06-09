package dev.extframework.boot

import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.Job
import kotlinx.cli.*
import dev.extframework.archives.*
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.component.*
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactRequest
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.artifact.SoftwareComponentRepositorySettings
import dev.extframework.boot.dependency.DependencyTypeContainer
import java.nio.file.Path

public const val API_VERSION: String = "2.1-SNAPSHOT"

public interface BootInstance {
    public val location: Path
    public val dependencyTypes: DependencyTypeContainer
    public val archiveGraph: ArchiveGraph
    public val componentResolver: SoftwareComponentResolver

    public fun isCached(descriptor: SoftwareComponentDescriptor) : Boolean

    public fun cache(request: SoftwareComponentArtifactRequest, location: SoftwareComponentRepositorySettings) : Job<Unit>

    @Throws(ArchiveException::class)
    public fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
    ): I
}

public inline fun <T : ComponentConfiguration, I : ComponentInstance<T>, reified F : ComponentFactory<T, I>> BootInstance.new(descriptor: SoftwareComponentDescriptor, configuration: T): I =
        this.new(descriptor, F::class.java, configuration)