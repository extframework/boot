package net.yakclient.boot.plugin.artifact

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
import net.yakclient.boot.plugin.PluginRuntimeModelDependency
import net.yakclient.boot.plugin.PluginRuntimeModel
import net.yakclient.boot.plugin.PluginRuntimeModelRepository.Companion.LOCAL_PLUGIN_REPO_TYPE
import net.yakclient.boot.plugin.PluginRuntimeModelRepository.Companion.PLUGIN_REPO_TYPE

public class PluginMetadataHandler(
    settings: PluginRepositorySettings,
) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    override fun requestMetadata(
        desc: PluginDescriptor,
    ): Either<MetadataRequestException, PluginArtifactMetadata> = either.eager {
        val (group, artifact, version, _) = desc

        // plugin runtime model
        val prmResource = layout.resourceOf(group, artifact, version, "prm", "json").bind()
        val jarResource = layout.resourceOf(group, artifact, version, null, "jar").orNull()

        val prm = mapper.readValue<PluginRuntimeModel>(prmResource.open())

        if (prm.packaging != PluginRuntimeModel.EMPTY_PACKAGING && jarResource == null) throw Exception("Missing artifact yet packaging type is not empty (was '${prm.packaging}').")

        PluginArtifactMetadata(
            desc,
            jarResource,
            prm.plugins.map {
                val pGroup = checkNotNull(it.request["group"]) {"Invalid plugin runtime model ('$desc')! Plugin dependency does not contain group name!"}
                val pArtifact = checkNotNull(it.request["artifact"]) {"Invalid plugin runtime model ('$desc')! Plugin dependency does not contain artifact name!"}
                val pVersion = checkNotNull(it.request["version"]) {"Invalid plugin runtime model ('$desc')! Plugin dependency does not contain version!"}

                val repository = run {
                    val repo by it::repository
                    check(repo.type == PLUGIN_REPO_TYPE || repo.type == LOCAL_PLUGIN_REPO_TYPE) { "Invalid repository type: ${repo.type} must be '$PLUGIN_REPO_TYPE'. Found in metadata of plugin: '$desc'" }

                    val url by repo.settings
                    val releasesEnabled = repo.settings["releasesEnabled"]?.toBoolean() ?: true
                    val snapshotsEnabled = repo.settings["snapshotsEnabled"]?.toBoolean() ?: true

                    PluginRepositoryStub(
                        PomRepository(
                            null,
                            null,
                            url,
                            repo.type,
                            PomRepositoryPolicy(releasesEnabled),
                            PomRepositoryPolicy(snapshotsEnabled)
                        )
                    )
                }

                PluginChildInfo(
                    PluginDescriptor(pGroup, pArtifact, pVersion, null),
                    listOf(repository),
                    "compile"
                )
            },
            prm.dependencies.map {
                PluginDependencyInfo(it.repository.type, it.request, it.repository.settings)
            },
            prm
        )
    }
}