package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.dependency.DependencyData
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

private const val VERSIONED_METADATA_NAME = "artifact-metadata.json"

public open class MavenDataAccess(
    private val path: Path,
) : DataAccess<SimpleMavenArtifactRequest, DependencyData<SimpleMavenArtifactRequest>> {
    private val mapper: ObjectMapper by lazy(::initMapper)

    private data class SerializableMavenDescriptor(
        val group: String,
        val artifact: String,
        val version: String,
        val classifier: String?,
    ) : ArtifactMetadata.Descriptor {
        @JsonIgnore
        override val name: String = ""
    }

    private data class SerializableMavenArtifactRequest(
        override val descriptor: SerializableMavenDescriptor,
        val isTransitive: Boolean = true,
        val includeScopes: Set<String> = setOf(),
        val excludeArtifacts: Set<String> = setOf(),
    ) : ArtifactRequest<SerializableMavenDescriptor>

    protected open fun initMapper(): ObjectMapper {
        val kotlinModule = KotlinModule.Builder()
            .build()

        val mapper = ObjectMapper()

        return mapper.registerModule(kotlinModule)
    }

    private fun readMetadataFile(versionedPath: Path): Map<SimpleMavenArtifactRequest, DependencyData<SimpleMavenArtifactRequest>> {
        val metadataPath = versionedPath resolve VERSIONED_METADATA_NAME

        if (!Files.exists(metadataPath)) {
            metadataPath.make()
            metadataPath.writeBytes(mapper.writeValueAsBytes(ArrayList<DependencyData<SimpleMavenArtifactRequest>>()))
        }

        val content = mapper.readValue<List<DependencyData<SerializableMavenArtifactRequest>>>(metadataPath.toFile())

        val mappedContent: List<DependencyData<SimpleMavenArtifactRequest>> = content.map {
            fun toRealRequest(
                req: SerializableMavenArtifactRequest,
            ): SimpleMavenArtifactRequest = SimpleMavenArtifactRequest(
                SimpleMavenDescriptor(
                    req.descriptor.group,
                    req.descriptor.artifact,
                    req.descriptor.version,
                    req.descriptor.classifier
                ),
                req.isTransitive,
                req.includeScopes,
                req.excludeArtifacts,
            )

            DependencyData(
                toRealRequest(it.key),
                it.archive,
                it.children.map(::toRealRequest)
            )
        }

        return mappedContent
            .associateBy { it.key }
    }

    private fun writeMetadataFile(
        versionedPath: Path,
        content: Map<SimpleMavenArtifactRequest, DependencyData<SimpleMavenArtifactRequest>>,
    ) {
        val metadataPath = versionedPath resolve VERSIONED_METADATA_NAME

        if (!Files.exists(metadataPath)) metadataPath.make()

        val list = content.values.toList().map {
            DependencyData(
                SerializableMavenArtifactRequest(
                    SerializableMavenDescriptor(
                        it.key.descriptor.group,
                        it.key.descriptor.artifact,
                        it.key.descriptor.version,
                        it.key.descriptor.classifier,
                    ),
                    it.key.isTransitive,
                    it.key.includeScopes,
                    it.key.excludeArtifacts
                ),
                it.archive,
                it.children.map { child ->
                    SerializableMavenArtifactRequest(
                        SerializableMavenDescriptor(
                            child.descriptor.group,
                            child.descriptor.artifact,
                            child.descriptor.version,
                            child.descriptor.classifier,
                        ),
                        child.isTransitive,
                        child.includeScopes,
                        child.excludeArtifacts
                    )
                }
            )
        }
        val value = mapper.writeValueAsBytes(list)

        metadataPath.writeBytes(value)
    }

    override fun read(key: SimpleMavenArtifactRequest): DependencyData<SimpleMavenArtifactRequest>? {
        val desc by key::descriptor

        val artifactPath =
            path resolve desc.group.replace('.', File.separatorChar) resolve desc.artifact resolve desc.version

        val metadata = readMetadataFile(artifactPath)

        return metadata[key]
    }

    override fun write(
        key: SimpleMavenArtifactRequest,
        value: DependencyData<SimpleMavenArtifactRequest>,
    ) {
        val desc by key::descriptor

        val versionedPath =
            path resolve desc.group.replace('.', File.separatorChar) resolve desc.artifact resolve desc.version

        val metadata = readMetadataFile(versionedPath)

        if (!metadata.containsKey(key)) {
            val metadataMap = metadata.toMutableMap().apply { put(key, value) }

            writeMetadataFile(versionedPath, metadataMap)
        }
    }
}