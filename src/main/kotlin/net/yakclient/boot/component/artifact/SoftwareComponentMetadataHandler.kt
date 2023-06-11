package net.yakclient.boot.component.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenMetadataHandler
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepositoryPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.component.SoftwareComponentModel
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.LOCAL
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.DEFAULT

public class SoftwareComponentMetadataHandler(
    settings: SoftwareComponentRepositorySettings,
) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    // TODO, refactor so that we use maven instead of doing settings/request parsing ourselves
    override fun requestMetadata(
        desc: SoftwareComponentDescriptor,
    ): Either<MetadataRequestException, SoftwareComponentArtifactMetadata> = either.eager {
        val (group, artifact, version, _) = desc

        val modelResource = layout.resourceOf(group, artifact, version, "component-model", "json").bind()
        val jarResource = layout.resourceOf(group, artifact, version, null, "jar").orNull()

        val model = mapper.readValue<SoftwareComponentModel>(modelResource.open())

        if (model.packaging != SoftwareComponentModel.EMPTY_PACKAGING && jarResource == null) throw Exception("Missing artifact yet packaging type is not empty (was '${model.packaging}').")

        SoftwareComponentArtifactMetadata(
            desc,
            jarResource,
            model.children.map {
                val descriptor = checkNotNull(it.request["descriptor"]) {"Invalid Software Component model ('$desc')! Component dependency does not contain descriptor!"}

                val repository = run {
                    val repo by it::repository
                    check(repo.type == DEFAULT || repo.type == LOCAL) { "Invalid repository type: ${repo.type} must be '$DEFAULT'. Found in metadata of plugin: '$desc'" }

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
                        )
                    )
                }

                SoftwareComponentChildInfo(
                    SoftwareComponentDescriptor.parseDescription(descriptor) ?: throw IllegalStateException("Failed to parse extension descriptor: '$descriptor' when loading extension '$desc'"),
                    listOf(repository),
                    "compile"
                )
            },
            model.dependencies.map {
                SoftwareComponentDependencyInfo(it.repository.type, it.request, it.repository.settings)
            },
            model
        )
    }
}