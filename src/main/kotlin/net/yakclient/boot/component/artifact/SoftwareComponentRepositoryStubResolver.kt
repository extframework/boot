package net.yakclient.boot.component.artifact

import com.durganmcbroom.artifact.resolver.RepositoryStubResolutionException
import com.durganmcbroom.artifact.resolver.RepositoryStubResolver
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.ResourceAlgorithm
import net.yakclient.boot.component.SoftwareComponentModelRepository

public class SoftwareComponentRepositoryStubResolver(
    private val preferredAlgorithm: ResourceAlgorithm,
) : RepositoryStubResolver<SoftwareComponentRepositoryStub, SoftwareComponentRepositorySettings> {
    override fun resolve(
        stub: SoftwareComponentRepositoryStub,
    ): Result<SoftwareComponentRepositorySettings> = result {
        val layout =
            if (stub.unresolvedRepository.layout == SoftwareComponentModelRepository.DEFAULT) SimpleMavenDefaultLayout(
                stub.unresolvedRepository.url,
                preferredAlgorithm,
                stub.unresolvedRepository.releases.enabled,
                stub.unresolvedRepository.snapshots.enabled,
                stub.requireResourceVerification,
            ) else if (stub.unresolvedRepository.layout == SoftwareComponentModelRepository.LOCAL) SimpleMavenRepositorySettings.local(
                stub.unresolvedRepository.url,
                preferredAlgorithm
            ).layout else throw RepositoryStubResolutionException(
                "Unknown layout: '${stub.unresolvedRepository.layout}"
            )

        SimpleMavenRepositorySettings(
            layout,
            preferredAlgorithm,
            requireResourceVerification = stub.requireResourceVerification,
        )
    }
}
