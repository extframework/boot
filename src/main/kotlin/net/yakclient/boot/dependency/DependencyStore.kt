package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.open
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.ArtifactArchiveKey
import net.yakclient.boot.ArtifactArchiveStore
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

public class DependencyStore(
    private val cachePath: Path,
    private val metadataProviders: List<CacheableMetadataProvider<*>>
) : ArtifactArchiveStore<DependencyData> {
    private val cacheMeta = cachePath.resolve("dependencies-meta.json")

    private val logger: Logger = Logger.getLogger(DependencyStore::class.simpleName)
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
    private class DependencyDataAccess(
        private val path: Path,
        private val providers : List<CacheableMetadataProvider<*>>,
    ) : DataAccess<ArtifactArchiveKey, DependencyData> {
        private val metaPath = path resolve "dependencies.json"

        private val cachedDependencies: MutableMap<String, DependencyData>
        private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

        init {
            val src = metaPath.toFile()

            if (metaPath.make()) src.writeText(mapper.writeValueAsString(setOf<CachedArtifact>()))

            val inMeta = mapper.readValue<Map<String, DependencyData>>(src)

            cachedDependencies = inMeta.toMutableMap()
        }

        private fun getProvider(d: ArtifactMetadata.Descriptor): CacheableMetadataProvider<ArtifactMetadata.Descriptor> =
            (providers.first { it.descriptorType.isInstance(d) } as? CacheableMetadataProvider<ArtifactMetadata.Descriptor>)
                ?: throw java.lang.IllegalArgumentException("Unknown descriptor type: '${d::class.simpleName}', no valid metadata provider found.")


        override fun read(key: ArtifactArchiveKey): DependencyData? {
            val skey = getProvider(key.desc)

            return cachedDependencies[skey]
        }

        override fun write(key: ArtifactArchiveKey, value: DependencyData) {
            val desc = key.desc
            val provider = getProvider(desc)

            // Check if we need to cache the jar
            val cacheJar = value.archive != null

            // Create a cached descriptor
            val skey = (provider.transformDescriptor(desc))
            // Check the in-memory cache to see if it has already been loaded, if it has then return it
            return cachedDependencies[skey] ?: run {
                // Create a path to where the artifact should be cached, if no version is present then making sure no extra '-' is included
                val jarPath = path resolve provider.relativePath(desc)

                // Creating the dependency to return.
//                val cachedDependency = CachedArtifact(
//                    jarPath.takeIf { cacheJar },
//
//                    // Mapping the dependencies to be pedantic
//                    value.map { CachedArtifact.CachedDescriptor(it.desc.name) },
//                    skey
//                )

                if (!Files.exists(jarPath) && cacheJar) {
//                    logger.log(Level.INFO, "Downloading dependency: ${artifact.desc}")

                    Channels.newChannel(value.archive!!.open()).use { cin ->
                        jarPath.make()
                        FileOutputStream(jarPath.toFile()).use { fout ->
                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                        }
                    }
                }
                // Updating the in-memory cache

                cachedDependencies[key] = value

                // Updating the out-of-memory cache
                metaPath.toFile().writeText(mapper.writeValueAsString(cachedDependencies))


                // Returning
                value
            }
        }
    }
}