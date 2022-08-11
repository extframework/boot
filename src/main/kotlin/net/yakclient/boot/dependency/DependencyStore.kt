package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.addDeserializer
import com.fasterxml.jackson.module.kotlin.addSerializer
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.ArtifactArchiveKey
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.store.DataAccess
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.LocalResource
import net.yakclient.common.util.resource.SafeResource
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KFunction1

public typealias DependencyStore = DataStore<ArtifactArchiveKey, DependencyData>

public fun DependencyStore(
    path: Path,
    providers: List<DependencyMetadataProvider<*>>,
    func: KFunction1<DataAccess<ArtifactArchiveKey, DependencyData>, DependencyStore> = ::CachingDataStore
): DependencyStore {
    return func.invoke(DependencyDataAccess(path, providers))
}

public fun DependencyStore.path(): Path = (access as DependencyDataAccess).path.toAbsolutePath()

private const val DESC_TYPE_FIELD = "desc_type"
private const val TYPE_FIELD = "desc"

private const val RESOURCE_URI_FIELD = "uri"

private class DependencyDataAccess(
    val path: Path,
    private val providers: List<DependencyMetadataProvider<*>>,
) : DataAccess<ArtifactArchiveKey, DependencyData> {
    private val metaPath = path resolve "dependencies.json"

    private val cachedDependencies: MutableMap<String, DependencyData>
    private val mapper: ObjectMapper
    private val logger: Logger = Logger.getLogger(this::class.simpleName)

    init {
        class DescriptionSerializer : StdSerializer<ArtifactMetadata.Descriptor>(
            ArtifactMetadata.Descriptor::class.java
        ) {
            override fun serialize(
                value: ArtifactMetadata.Descriptor,
                gen: JsonGenerator,
                provider: SerializerProvider
            ) {
                val p = getProvider(value)

                gen.writeStartObject()
                gen.writeStringField(DESC_TYPE_FIELD, p.descriptorType.qualifiedName)
                gen.writeStringField(TYPE_FIELD, p.descToString(value))
                gen.writeEndObject()
            }
        }

        class DescriptionDeserializer :
            StdDeserializer<ArtifactMetadata.Descriptor>(
                ArtifactMetadata.Descriptor::class.java
            ) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): ArtifactMetadata.Descriptor {
                val codec = p.codec.readTree<JsonNode>(p)

                val type = codec[DESC_TYPE_FIELD].asText()
                val descString = codec[TYPE_FIELD].asText()
                return providers.find { it.descriptorType.qualifiedName == type }?.stringToDesc(descString) ?: run {
                    logger.log(
                        Level.WARNING, """
                        Found descriptor type: '$type' but no 
                        applicable metadata provider for this 
                        type was found! This is being ignored 
                        for now but the dependency cache should 
                        be redone. THIS dependency cache located
                        at: '${path.toAbsolutePath()}'.""".trimIndent()
                    )

                    object : ArtifactMetadata.Descriptor {
                        override val name: String = "ERR. Not able to parse descriptor!!!"
                    }
                }
            }
        }

        class SafeResourceSerializer : StdSerializer<SafeResource>(SafeResource::class.java) {
            override fun serialize(value: SafeResource, gen: JsonGenerator, provider: SerializerProvider?) {
                gen.writeStartObject()
                gen.writeStringField(RESOURCE_URI_FIELD, value.uri.toString())
                gen.writeEndObject()
            }
        }

        class SafeResourceDeserializer : StdDeserializer<SafeResource>(SafeResource::class.java) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): SafeResource {
                val codec = p.codec.readTree<JsonNode>(p)

                return LocalResource(URI.create(codec[RESOURCE_URI_FIELD].asText()))
            }
        }

        mapper = ObjectMapper().registerModule(
            KotlinModule().addSerializer(
                ArtifactMetadata.Descriptor::class,
                DescriptionSerializer()
            ).addDeserializer(
                ArtifactMetadata.Descriptor::class,
                DescriptionDeserializer()
            ).addSerializer(
                SafeResource::class.java,
                SafeResourceSerializer()
            ).addDeserializer(
                SafeResource::class.java,
                SafeResourceDeserializer()
            )
        )

        val src = metaPath.toFile()

        if (metaPath.make()) src.writeText(mapper.writeValueAsString(HashMap<String, DependencyData>()))

        val inMeta = mapper.readValue<Map<String, DependencyData>>(src)

        cachedDependencies = inMeta.toMutableMap()
    }

    private fun getProvider(d: ArtifactMetadata.Descriptor): DependencyMetadataProvider<ArtifactMetadata.Descriptor> =
        (providers.find { it.descriptorType.isInstance(d) } as? DependencyMetadataProvider<ArtifactMetadata.Descriptor>)
            ?: throw java.lang.IllegalArgumentException("Unknown descriptor type: '${d::class.simpleName}', no valid metadata provider found.")

    override fun read(key: ArtifactArchiveKey): DependencyData? {
        val skey = getProvider(key.desc).descToString(key.desc)

        return cachedDependencies[skey]
    }

    override fun write(key: ArtifactArchiveKey, value: DependencyData) {
        val desc = key.desc
        val provider = getProvider(desc)

        // Check if we need to cache the archive
        val cacheJar = value.archive != null

        // Create a cached descriptor
        val skey = (provider.descToString(desc))
        // Check the in-memory cache to see if it has already been loaded, if it has then return it
        // Create a path to where the artifact should be cached, if no version is present then making sure no extra '-' is included
        val jarPath = path resolve provider.relativePath(desc) resolve provider.jarName(desc)

        val data = value.copy(
            archive = value.archive?.let {
                object : SafeResource {
                    override val uri: URI = jarPath.toUri()

                    override fun open(): InputStream = jarPath.toFile().inputStream()
                }
            }
        )

        if (!Files.exists(jarPath) && cacheJar) {
            logger.log(Level.INFO, "Downloading dependency: '$desc'")

            Channels.newChannel(value.archive!!.open()).use { cin ->
                jarPath.make()
                FileOutputStream(jarPath.toFile()).use { fout ->
                    fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                }
            }
        }

        // Updating the in-memory cache
        cachedDependencies[skey] = data

        // Updating the out-of-memory cache
        metaPath.toFile().writeText(mapper.writeValueAsString(cachedDependencies))
    }
}