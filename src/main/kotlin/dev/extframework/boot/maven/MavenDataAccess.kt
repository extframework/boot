package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.dependency.DependencyData
import dev.extframework.boot.store.DataAccess
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

private const val VERSIONED_METADATA_NAME = "artifact-metadata.json"

public open class MavenDataAccess(
        private val path: Path,
) : DataAccess<SimpleMavenDescriptor, DependencyData<SimpleMavenDescriptor>> {
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

    protected open fun initMapper(): ObjectMapper {
        val kotlinModule = KotlinModule.Builder().build()

        val mapper = ObjectMapper()

        return mapper.registerModule(kotlinModule)
    }

    private fun readMetadataFile(versionedPath: Path): Map<SimpleMavenDescriptor, DependencyData<SimpleMavenDescriptor>> {
        val metadataPath = versionedPath resolve VERSIONED_METADATA_NAME

        if (!Files.exists(metadataPath)) {
            metadataPath.make()
            metadataPath.writeBytes(mapper.writeValueAsBytes(ArrayList<DependencyData<SerializableMavenDescriptor>>()))
        }

        val content = mapper.readValue<List<DependencyData<SerializableMavenDescriptor>>>(
            Files.newInputStream(metadataPath)
        )

        val mappedContent: List<DependencyData<SimpleMavenDescriptor>> = content.map {
            fun toRealDescriptor(
                    req: SerializableMavenDescriptor,
            ): SimpleMavenDescriptor = SimpleMavenDescriptor(req.group, req.artifact, req.version, req.classifier)


            DependencyData(toRealDescriptor(it.key), it.archive, it.children.map(::toRealDescriptor))
        }

        return mappedContent.associateBy { it.key }
    }

    private fun writeMetadataFile(
            versionedPath: Path,
            content: Map<SimpleMavenDescriptor, DependencyData<SimpleMavenDescriptor>>,
    ) {
        val metadataPath = versionedPath resolve VERSIONED_METADATA_NAME

        if (!Files.exists(metadataPath)) metadataPath.make()

        val list = content.values.toList().map {
            DependencyData(SerializableMavenDescriptor(
                    it.key.group,
                    it.key.artifact,
                    it.key.version,
                    it.key.classifier,
            ), it.archive, it.children.map { child ->
                SerializableMavenDescriptor(
                        child.group,
                        child.artifact,
                        child.version,
                        child.classifier,
                )
            })
        }
        val value = mapper.writeValueAsBytes(list)

        metadataPath.writeBytes(value)
    }

    override fun read(key: SimpleMavenDescriptor): DependencyData<SimpleMavenDescriptor>? {
        val artifactPath = path resolve key.group.replace('.', File.separatorChar) resolve key.artifact resolve key.version

        val metadata = readMetadataFile(artifactPath)

        return metadata[key]
    }

    override fun write(
            key: SimpleMavenDescriptor,
            value: DependencyData<SimpleMavenDescriptor>,
    ) {
        val versionedPath = path resolve key.group.replace('.', File.separatorChar) resolve key.artifact resolve key.version

        val metadata = readMetadataFile(versionedPath)

        if (!metadata.containsKey(key)) {
            val metadataMap = metadata.toMutableMap().apply { put(key, value) }

            writeMetadataFile(versionedPath, metadataMap)
        }
    }
}