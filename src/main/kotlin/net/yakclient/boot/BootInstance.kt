package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.JobResult
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyTypeContainer
import java.nio.file.Path

public interface BootInstance {
    public val location: Path
    public val dependencyTypes: DependencyTypeContainer
    public val archiveGraph: ArchiveGraph
    public val componentResolver: SoftwareComponentResolver

    public fun isCached(descriptor: SoftwareComponentDescriptor) : Boolean

    public suspend fun cache(request: SoftwareComponentArtifactRequest, location: SoftwareComponentRepositorySettings) : JobResult<Unit, ArchiveException>

    @Throws(ArchiveException::class)
    public fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
    ): I
}

public inline fun <T : ComponentConfiguration, I : ComponentInstance<T>, reified F : ComponentFactory<T, I>> BootInstance.new(descriptor: SoftwareComponentDescriptor, configuration: T): I =
        this.new(descriptor, F::class.java, configuration)