package dev.extframework.boot.component.artifact

import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenMetadataHandler
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepositoryPolicy
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.component.SoftwareComponentModel
import dev.extframework.boot.component.SoftwareComponentModelRepository.Companion.DEFAULT
import dev.extframework.boot.component.SoftwareComponentModelRepository.Companion.LOCAL

public class SoftwareComponentMetadataHandler(
    settings: SoftwareComponentRepositorySettings,
) : SimpleMavenMetadataHandler(
    settings
) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    override fun parseDescriptor(desc: String): Result<SimpleMavenDescriptor> = result {
        SimpleMavenDescriptor.parseDescription(desc) ?: throw MetadataRequestException.DescriptorParseFailed
    }


    override fun requestMetadata(
        desc: SoftwareComponentDescriptor,
    ): Job<SoftwareComponentArtifactMetadata> =
        job(JobName("Request metadata for software component: '${desc}'")) {
            val (group, artifact, version, _) = desc

            val modelResource = layout.resourceOf(group, artifact, version, "component-model", "json")().merge()
            val jarResource = layout.resourceOf(group, artifact, version, null, "jar")().merge()

            val model = mapper.readValue<SoftwareComponentModel>(
                modelResource.openStream()
            )

            if (model.packaging != SoftwareComponentModel.EMPTY_PACKAGING && jarResource == null) throw Exception("Missing artifact yet packaging type is not empty (was '${model.packaging}').")

            SoftwareComponentArtifactMetadata(
                desc,
                jarResource,
                model.children.map {
                    val descriptor =
                        checkNotNull(it.request["descriptor"]) { "Invalid Software Component model ('$desc')! Component dependency does not contain descriptor!" }

                    val repository = run {
                        val repo by it::repository
                        check(repo.type == DEFAULT || repo.type == LOCAL) { "Invalid repository type: ${repo.type} must be '$DEFAULT' or '$LOCAL'. Found in metadata of component: '$desc'" }

                        val location by repo.settings
                        val releasesEnabled = repo.settings["releasesEnabled"]?.toBoolean() ?: true
                        val snapshotsEnabled = repo.settings["snapshotsEnabled"]?.toBoolean() ?: true

                        SoftwareComponentRepositoryStub(
                            PomRepository(
                                null,
                                null,
                                location,
                                repo.type,
                                PomRepositoryPolicy(releasesEnabled),
                                PomRepositoryPolicy(snapshotsEnabled)
                            ),
                            settings.requireResourceVerification
                        )
                    }

                    SoftwareComponentChildInfo(
                        SoftwareComponentDescriptor.parseDescription(descriptor)
                            ?: throw IllegalStateException("Failed to parse extension descriptor: '$descriptor' when loading extension '$desc'"),
                        listOf(repository),
                        "compile"
                    )
                },
                model.dependencies.map {
                    SoftwareComponentDependencyInfo(
                        it.repository.type,
                        it.request,
                        it.repository.settings
                    )
                },
                model
            )
        }
}