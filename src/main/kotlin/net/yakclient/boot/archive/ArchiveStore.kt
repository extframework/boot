package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.open
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.dependency.CacheableMetadataProvider
import net.yakclient.boot.dependency.CachedArtifact
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

public class ArchiveStore(
    private val cachePath: Path,
    private val metadataProviders: List<CacheableMetadataProvider<*>>
) {
    private val cacheMeta = cachePath.resolve("dependencies-meta.json")

    private val logger: Logger = Logger.getLogger(ArchiveStore::class.simpleName)
    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    private val all: MutableMap<CachedArtifact.CachedDescriptor, CachedArtifact>

    init {
        val metaFile = cacheMeta.toFile()

        if (cacheMeta.make()) metaFile.writeText(mapper.writeValueAsString(setOf<CachedArtifact>()))

        all = mapper.readValue<Set<CachedArtifact>>(metaFile).associateByTo(ConcurrentHashMap()) { it.desc }
    }

    private fun getProvider(d: ArtifactMetadata.Descriptor): CacheableMetadataProvider<ArtifactMetadata.Descriptor> =
        (metadataProviders.first { it.descriptorType.isInstance(d) } as? CacheableMetadataProvider<ArtifactMetadata.Descriptor>)
            ?: throw java.lang.IllegalArgumentException("Unknown descriptor type: '${d::class.simpleName}', no valid metadata provider found.")

    public operator fun get(d: ArtifactMetadata.Descriptor): CachedArtifact? =
        all[getProvider(d).transformDescriptor(d)]

    public fun cache(artifact: ArtifactMetadata<*, *>): CachedArtifact {
        val desc = artifact.desc
        val provider = getProvider(desc)

        // Check if we need to cache the jar
        val cacheJar = artifact.resource != null

        // Create a cached descriptor
        val key = (provider.transformDescriptor(desc))
        // Check the in-memory cache to see if it has already been loaded, if it has then return it
        return all[key] ?: run {
            // Create a path to where the artifact should be cached, if no version is present then making sure no extra '-' is included
            val jarPath = cachePath resolve provider.relativePath(desc)

            // Creating the dependency to return.
            val cachedDependency = CachedArtifact(
                jarPath.takeIf { cacheJar },

                // Mapping the dependencies to be pedantic
                artifact.transitives.map { CachedArtifact.CachedDescriptor(it.desc.name) },
                key
            )

            if (!Files.exists(jarPath) && cacheJar) {
                logger.log(Level.INFO, "Downloading dependency: ${artifact.desc}")

                Channels.newChannel(artifact.resource!!.open()).use { cin ->
                    jarPath.make()
                    FileOutputStream(jarPath.toFile()).use { fout ->
                        fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                    }
                }
            }

            // Getting all the cache dependencies
            val meta = all.values.toMutableSet()

            // Adding the current dependency to the ones we've already cached
            meta.add(cachedDependency)

            // Overwriting the meta file with the updated dependencies
            cacheMeta.toFile().writeText(mapper.writeValueAsString(meta))

            // Updating the in-memory cache
            all[cachedDependency.desc] = cachedDependency

            // Returning
            cachedDependency
        }
    }
}