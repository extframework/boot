package net.yakclient.boot.component

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.store.DataAccess
import net.yakclient.boot.util.addDeserializer
import net.yakclient.boot.util.addSerializer
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

private const val METADATA_FILE_NAME = "component.json"

private const val GROUP_FIELD_NAME = "group"
private const val ARTIFACT_FIELD_NAME = "artifact"
private const val VERSION_FIELD_NAME = "version"
private const val CLASSIFIER_FIELD_NAME = "classifier"

public class SoftwareComponentDataAccess(
    private val path: Path,
) : DataAccess<SoftwareComponentDescriptor, SoftwareComponentData> {
    private val mapper : ObjectMapper

    init {
        val kotlinModule = KotlinModule.Builder().build()

        kotlinModule.addSerializer(SimpleMavenDescriptor::class) { it, gen, _ ->
            gen.writeStartObject()
            gen.writeStringField(GROUP_FIELD_NAME, it.group)
            gen.writeStringField(ARTIFACT_FIELD_NAME, it.artifact)
            gen.writeStringField(VERSION_FIELD_NAME, it.version)
            gen.writeStringField(CLASSIFIER_FIELD_NAME, it.classifier)
            gen.writeEndObject()
        }

        kotlinModule.addDeserializer(SimpleMavenDescriptor::class) { parser, _ ->
            val tree = parser.codec.readTree<JsonNode>(parser)

            SimpleMavenDescriptor(
                tree[GROUP_FIELD_NAME].asText(),
                tree[ARTIFACT_FIELD_NAME].asText(),
                tree[VERSION_FIELD_NAME].asText(),
                tree[CLASSIFIER_FIELD_NAME].asText()
            )
        }

        val mapper = ObjectMapper().registerModule(kotlinModule)

        this.mapper = mapper
    }

    override fun read(key: SoftwareComponentDescriptor): SoftwareComponentData? {

        val versionedPath = path resolve
                key.group.replace('.', File.separatorChar) resolve
                key.artifact resolve
                key.version

        val metadataPath = versionedPath resolve METADATA_FILE_NAME

        if (!Files.exists(metadataPath)) return null

        return mapper.readValue<SoftwareComponentData>(metadataPath.toFile())
    }

    override fun write(key: SoftwareComponentDescriptor, value: SoftwareComponentData) {
        val versionedPath = path resolve
                key.group.replace('.', File.separatorChar) resolve
                key.artifact resolve
                key.version

        val metadataPath = versionedPath resolve METADATA_FILE_NAME

        if (!Files.exists(metadataPath)) metadataPath.make()

        metadataPath.writeBytes(mapper.writeValueAsBytes(value))
    }
}